
package likelion.yacha_backend.domain.adsense.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

import likelion.yacha_backend.domain.adsense.client.AdSenseTokenClient;
import likelion.yacha_backend.domain.adsense.config.AdSenseProperties;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AdSenseOAuthService {

    public static final String COOKIE_NAME = "yp_adsense_oauth";

    public static final String CALLBACK_PATH =
            "/api/v1/adsense/oauth/callback";

    private static final String GOOGLE_AUTH_URL =
            "https://accounts.google.com/o/oauth2/v2/auth";

    private static final String SCOPE =
            "https://www.googleapis.com/auth/adsense.readonly";

    private static final String STATE_PREFIX =
            "adsense:oauth:state:";

    private static final Duration STATE_TTL =
            Duration.ofMinutes(10);

    private final AdSenseProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final AdSenseTokenClient tokenClient;
    private final AdSenseCredentialService credentialService;
    private final SecureRandom random = new SecureRandom();

    public AdSenseOAuthService(
            AdSenseProperties properties,
            StringRedisTemplate redisTemplate,
            AdSenseTokenClient tokenClient,
            AdSenseCredentialService credentialService
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.tokenClient = tokenClient;
        this.credentialService = credentialService;
    }

    public AuthorizationStart start(Long adminUserId) {
        ensureConfigured();

        if (adminUserId == null || adminUserId <= 0) {
            throw new AdSenseOAuthException(
                    HttpStatus.FORBIDDEN,
                    "관리자 인증이 필요합니다."
            );
        }

        String state = randomToken();
        String nonce = randomToken();

        // OAuth 상태와 브라우저 검증값을 Redis에 저장한다.
        redisTemplate.opsForValue().set(
                STATE_PREFIX + state,
                adminUserId + ":" + sha256(nonce),
                STATE_TTL
        );

        String url = UriComponentsBuilder
                .fromUriString(GOOGLE_AUTH_URL)
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();

        return new AuthorizationStart(url, nonce);
    }

    public void complete(
            String state,
            String cookieNonce,
            String code,
            String error
    ) {
        ensureConfigured();

        if (!validToken(state) || !validToken(cookieNonce)) {
            throw new AdSenseOAuthException(
                    HttpStatus.BAD_REQUEST,
                    "OAuth 인증 상태가 유효하지 않습니다."
            );
        }

        // Redis에서 한 번 읽은 state는 삭제한다.
        String saved = redisTemplate.opsForValue()
                .getAndDelete(STATE_PREFIX + state);

        int separator = saved == null
                ? -1
                : saved.indexOf(':');

        if (separator <= 0) {
            throw new AdSenseOAuthException(
                    HttpStatus.BAD_REQUEST,
                    "OAuth 요청이 만료되었거나 사용되었습니다."
            );
        }

        String expectedHash = saved.substring(separator + 1);

        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                sha256(cookieNonce).getBytes(StandardCharsets.UTF_8))) {

            throw new AdSenseOAuthException(
                    HttpStatus.BAD_REQUEST,
                    "OAuth 브라우저 검증에 실패했습니다."
            );
        }

        if (StringUtils.hasText(error)) {
            throw new AdSenseOAuthException(
                    HttpStatus.BAD_REQUEST,
                    "Google 계정 연결이 취소되었습니다."
            );
        }

        if (!StringUtils.hasText(code)) {
            throw new AdSenseOAuthException(
                    HttpStatus.BAD_REQUEST,
                    "Google 인증 코드가 없습니다."
            );
        }

        long adminId;

        try {
            adminId = Long.parseLong(
                    saved.substring(0, separator)
            );
        } catch (NumberFormatException e) {
            throw new AdSenseOAuthException(
                    HttpStatus.BAD_REQUEST,
                    "OAuth 요청 정보가 잘못되었습니다."
            );
        }

        // Google 인증 코드를 토큰으로 교환한다.
        AdSenseTokenClient.TokenResult tokens =
                tokenClient.exchangeCode(code);

        // 사용자가 허용한 Google 계정이 맞는지 확인한다.
        tokenClient.verifyExpectedAccount(
                tokens.accessToken()
        );

        // Refresh Token은 암호화해서 DB에 저장한다.
        credentialService.store(
                adminId,
                properties.expectedAccountName(),
                tokens.refreshToken()
        );
    }

    private void ensureConfigured() {
        if (!properties.enabled()) {
            throw new AdSenseOAuthException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AdSense 통계 연결 기능이 비활성화되어 있습니다."
            );
        }

        if (!StringUtils.hasText(properties.clientId())
                || !StringUtils.hasText(properties.clientSecret())
                || !StringUtils.hasText(properties.redirectUri())
                || !StringUtils.hasText(properties.expectedAccountName())
                || !StringUtils.hasText(properties.tokenEncryptionKey())) {

            throw new AdSenseOAuthException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AdSense 연결 환경변수가 누락되었습니다."
            );
        }
    }

    private String randomToken() {
        byte[] buffer = new byte[32];
        random.nextBytes(buffer);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(buffer);
    }

    private static boolean validToken(String value) {
        return value != null
                && value.matches("[A-Za-z0-9_-]{43}");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    e
            );
        }
    }

    public record AuthorizationStart(
            String authorizationUrl,
            String cookieNonce
    ) {
    }
}
