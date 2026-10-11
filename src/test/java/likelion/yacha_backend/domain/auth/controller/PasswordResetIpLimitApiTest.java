package likelion.yacha_backend.domain.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import likelion.yacha_backend.domain.auth.repository.InMemoryIpRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비밀번호 찾기의 IP별 호출 제한. 한도를 3번으로 줄여서 확인함
 * 인증번호 흐름 자체는 {@link PasswordResetApiTest}
 */
@SpringBootTest(properties = {
        "auth.password-reset.request-ip-limit.max-requests=3",
        "auth.password-reset.verify-ip-limit.max-requests=3"
})
@AutoConfigureMockMvc
@DisplayName("비밀번호 찾기 IP별 호출 제한")
class PasswordResetIpLimitApiTest {

    private static final String CLIENT_IP = "203.0.113.7";

    /** 이메일별 재요청 제한(1분)에 걸리지 않게 요청마다 다른 이메일을 씀 */
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryIpRateLimiter ipRateLimiter;

    @BeforeEach
    void clearLimiter() {
        ipRateLimiter.clear();
    }

    private static String anyEmail() {
        return "ip-limit-%d@example.com".formatted(SEQ.incrementAndGet());
    }

    private ResultActions requestReset(String email, String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/reset-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email))
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                }));
    }

    private ResultActions verify(String email, String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "code": "123456"}
                        """.formatted(email))
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                }));
    }

    @Test
    @DisplayName("이메일을 바꿔 가며 요청해도 같은 IP는 정해진 횟수를 넘으면 429 TOO_MANY_REQUESTS")
    void limitsResetRequestsPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestReset(anyEmail(), CLIENT_IP).andExpect(status().isOk());
        }

        requestReset(anyEmail(), CLIENT_IP)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("요청 제한은 IP마다 따로 세서 다른 IP는 막히지 않는다")
    void resetRequestLimitIsPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestReset(anyEmail(), CLIENT_IP);
        }

        requestReset(anyEmail(), "198.51.100.2").andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증번호 확인도 같은 IP는 정해진 횟수를 넘으면 429 TOO_MANY_REQUESTS")
    void limitsVerifyPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            // 요청한 적 없는 이메일이라 RESET_CODE_EXPIRED 지만, 호출 수는 센다
            verify(anyEmail(), CLIENT_IP).andExpect(jsonPath("$.error.code").value("RESET_CODE_EXPIRED"));
        }

        verify(anyEmail(), CLIENT_IP)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("요청과 확인은 따로 센다")
    void requestAndVerifyAreCountedSeparately() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestReset(anyEmail(), CLIENT_IP);
        }

        verify(anyEmail(), CLIENT_IP).andExpect(jsonPath("$.error.code").value("RESET_CODE_EXPIRED"));
    }
}
