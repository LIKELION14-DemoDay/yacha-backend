package likelion.yacha_backend.domain.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import likelion.yacha_backend.global.config.S3Properties;
import likelion.yacha_backend.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class PhilosopherImageControllerTest {

    private S3Client s3Client;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        S3Properties props = new S3Properties(
                "test-bucket", "ap-northeast-2", "profiles", Duration.ofHours(1)
        );
        mvc = MockMvcBuilders.standaloneSetup(
                new PhilosopherImageController(s3Client, props)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void svgReturns200WithSvgContentType() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>"
                .getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), svg)
        );

        mvc.perform(get("/api/v1/philosopher-images/kant.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(content().bytes(svg))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")));
    }

    @Test
    void legacyPngStillWorks() throws Exception {
        byte[] png = new byte[]{1, 2, 3};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), png)
        );

        mvc.perform(get("/api/v1/philosopher-images/kant.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
    }

    @Test
    void invalidFilenameReturns404WithoutS3Request() throws Exception {
        mvc.perform(get("/api/v1/philosopher-images/foo.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("IMAGE_NOT_FOUND"));
        verifyNoInteractions(s3Client);
    }

    @Test
    void missingS3ObjectReturns404() throws Exception {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(404).message("NoSuchKey").build()
        );

        mvc.perform(get("/api/v1/philosopher-images/kant.svg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    void s3FailureReturns502() throws Exception {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(403).message("AccessDenied").build()
        );

        mvc.perform(get("/api/v1/philosopher-images/kant.svg"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("S3_IMAGE_READ_FAILED"));
    }

    @Test
    void sdkConnectionFailureReturns502() throws Exception {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(
                SdkClientException.builder().message("Connection failed").build()
        );

        mvc.perform(get("/api/v1/philosopher-images/kant.svg"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("S3_IMAGE_READ_FAILED"));
    }
}
