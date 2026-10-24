package likelion.yacha_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import likelion.yacha_backend.domain.auth.repository.InMemoryEmailCheckLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 프록시 뒤에서 실제 클라이언트 IP로 호출 수를 세는지
 *
 * server.forward-headers-strategy=native 는 Tomcat 이 처리해서 MockMvc 로는 확인할 수 없음
 * 그래서 실제 서버를 띄우고 HTTP 로 부름. 테스트 요청은 127.0.0.1 에서 오므로 프록시를 거친 요청과 같음
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "auth.email-check.max-requests=3")
@DisplayName("프록시 뒤 클라이언트 IP")
class ForwardedClientIpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private InMemoryEmailCheckLimiter limiter;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void clearLimiter() {
        limiter.clear();
    }

    private int check(String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/auth/email/availability?email=new@example.com"))
                .header("X-Forwarded-For", forwardedFor)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    @DisplayName("X-Forwarded-For 의 클라이언트 IP마다 따로 센다 (프록시 주소 하나로 묶이지 않음)")
    void countsPerForwardedIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(check("203.0.113.7")).isEqualTo(200);
        }

        assertThat(check("203.0.113.7")).isEqualTo(429);
        assertThat(check("198.51.100.2")).isEqualTo(200);
    }

    @Test
    @DisplayName("클라이언트가 X-Forwarded-For 앞쪽을 꾸며도 프록시가 붙인 마지막 IP로 센다")
    void ignoresSpoofedLeftmostIp() throws Exception {
        // 프록시(nginx 등)는 받은 헤더 뒤에 실제 접속 IP를 덧붙임: "클라이언트가 보낸 값, 실제 IP"
        for (int i = 0; i < 3; i++) {
            assertThat(check("10.0.0." + i + ", 203.0.113.7")).isEqualTo(200);
        }

        assertThat(check("1.2.3.4, 203.0.113.7")).isEqualTo(429);
    }
}
