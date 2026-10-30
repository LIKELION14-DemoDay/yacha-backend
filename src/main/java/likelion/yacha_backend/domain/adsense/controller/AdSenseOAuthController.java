
package likelion.yacha_backend.domain.adsense.controller;

import java.time.Duration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletResponse;

import likelion.yacha_backend.domain.adsense.config.AdSenseProperties;
import likelion.yacha_backend.domain.adsense.service.AdSenseOAuthException;
import likelion.yacha_backend.domain.adsense.service.AdSenseOAuthService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;

import org.slf4j.MDC;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdSense 관리", description = "관리자용 OAuth 연결 및 Google 콜백")
@RestController
public class AdSenseOAuthController {

    private final AdSenseOAuthService service;
    private final AdSenseProperties properties;

    public AdSenseOAuthController(
            AdSenseOAuthService service,
            AdSenseProperties properties
    ) {
        this.service = service;
        this.properties = properties;
    }

    @Operation(summary = "AdSense 인증 URL 발급", description = "관리자 JWT가 필요하며, 반환된 URL을 반드시 같은 브라우저에서 열어야 합니다.")
    @PostMapping("/api/v1/admin/ads/oauth/authorize")
    public ResponseEntity<ApiResponse<AuthorizeUrlResponse>> authorize(
            @AuthenticationPrincipal AuthUser admin
    ) {
        AdSenseOAuthService.AuthorizationStart start =
                service.start(admin.getUserId());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        nonceCookie(
                                start.cookieNonce(),
                                Duration.ofMinutes(10)
                        ).toString()
                )
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.success(
                                new AuthorizeUrlResponse(
                                        start.authorizationUrl()
                                )
                        )
                );
    }

    @Operation(summary = "Google AdSense OAuth 콜백", description = "Google에서 전달한 코드를 처리하고 브라우저 쿠키와 Redis state를 검증합니다.")
    @GetMapping(
            value = AdSenseOAuthService.CALLBACK_PATH,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @CookieValue(
                    name = AdSenseOAuthService.COOKIE_NAME,
                    required = false
            ) String cookieNonce,
            HttpServletResponse response
    ) {
        try {
            service.complete(
                    state,
                    cookieNonce,
                    code,
                    error
            );

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("Referrer-Policy", "no-referrer")
                    .body(
                            "AdSense 계정 연결이 완료되었습니다. 이 창을 닫아도 됩니다."
                    );

        } finally {
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    nonceCookie("", Duration.ZERO).toString()
            );
        }
    }

    @ExceptionHandler(AdSenseOAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleOAuthException(
            AdSenseOAuthException e
    ) {
        return ResponseEntity.status(e.status())
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(
                        new ApiResponse<>(
                                false,
                                null,
                                new ApiResponse.ErrorDetail(
                                        "ADSENSE_OAUTH_ERROR",
                                        e.getMessage()
                                ),
                                MDC.get("traceId")
                        )
                );
    }

    private ResponseCookie nonceCookie(
            String value,
            Duration age
    ) {
        return ResponseCookie
                .from(AdSenseOAuthService.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(AdSenseOAuthService.CALLBACK_PATH)
                .maxAge(age)
                .build();
    }

    public record AuthorizeUrlResponse(
            String authorizationUrl
    ) {
    }
}
