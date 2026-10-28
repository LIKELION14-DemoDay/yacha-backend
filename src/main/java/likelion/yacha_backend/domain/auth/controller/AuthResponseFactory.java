package likelion.yacha_backend.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import likelion.yacha_backend.domain.auth.dto.AuthResponse;
import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.balancegame.service.PreviewResultApplier;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.cookie.CookieProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 토큰 두 개를 HTTP 응답으로 포장
 *
 * 액세스 토큰 → 응답 body (프론트가 읽어서 헤더에 실어야 하므로)
 * 리프레시 토큰 → Set-Cookie (HttpOnly라 JS가 읽지 못함)
 *
 * 리프레시 토큰을 body에도 넣으면 JS가 읽을 수 있게 되어 HttpOnly로 막은 의미가 사라짐
 */
@Component
@RequiredArgsConstructor
public class AuthResponseFactory {

    private final CookieProvider cookieProvider;
    private final PreviewResultApplier previewResultApplier;

    /** 재발급 · 비밀번호 변경처럼 이미 로그인된 사람의 토큰을 바꾸는 응답 */
    public ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieProvider.createRefreshCookie(tokens.refreshToken()).toString())
                .body(ApiResponse.success(tokens.toResponse()));
    }

    /**
     * 로그인되는 응답 (게스트 생성 · 회원가입 · 로그인 · 소셜 로그인 · 승격)
     * 로그인 전 온보딩에서 한 밸런스 게임 결과가 쿠키로 와 있으면 계정에 저장함
     */
    public ResponseEntity<ApiResponse<AuthResponse>> forLogin(IssuedTokens tokens, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieProvider.createRefreshCookie(tokens.refreshToken()).toString());
        previewResultApplier.applyOnLogin(request, tokens.user().getId(), tokens.user().isGuest())
                .ifPresent(cookie -> response.header(HttpHeaders.SET_COOKIE, cookie.toString()));
        return response.body(ApiResponse.success(tokens.toResponse()));
    }
}
