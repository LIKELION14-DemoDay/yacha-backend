package likelion.yacha_backend.domain.auth.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 {@code mail.*}
 *
 * @param frontendBaseUrl 메일에 넣을 링크의 앞부분. 재설정 페이지는 프론트가 그리고,
 *                        서버는 주소만 만들어 보냄. 환경마다 달라서 설정으로 뺐음.
 * @param resetPath       재설정 페이지 경로. 프론트와 맞춰야 함 (확정 전까지 기본값 사용)
 */
@ConfigurationProperties(prefix = "mail")
public record MailProperties(String frontendBaseUrl, String resetPath) {

    /** 예: {@code https://yacha.com/reset-password?token=abc123} */
    public String passwordResetUrl(String token) {
        // 설정값 끝의 '/'와 경로 앞의 '/'가 겹쳐 "//"가 되는 것을 막음
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        String path = resetPath.startsWith("/") ? resetPath : "/" + resetPath;
        return base + path + "?token=" + token;
    }
}
