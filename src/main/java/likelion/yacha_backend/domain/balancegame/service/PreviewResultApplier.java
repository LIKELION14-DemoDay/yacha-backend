package likelion.yacha_backend.domain.balancegame.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import likelion.yacha_backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 로그인 전 온보딩에서 한 밸런스 게임 결과를 로그인할 때 계정에 저장
 *
 * 게스트 생성 · 회원가입 · 로그인 · 소셜 로그인 · 승격 응답에서 부름 (재발급 · 비밀번호 변경은 부르지 않음)
 * 아직 밸런스 게임을 안 한 계정에만 저장하고, 이미 결과가 있거나 건너뛴 계정은 그대로 둠
 *
 * 회원 계정에 왔으면 쿠키를 지움
 * 게스트 계정에 왔으면 쿠키를 남겨 둠. 게스트가 소셜로 가입하면 새 계정이라, 그때도 결과가 이어지게 하려고
 * 쿠키가 잘못됐으면(만료 · 변조) 지움
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreviewResultApplier {

    private final PreviewResultCookie previewResultCookie;
    private final UserService userService;

    /**
     * @return 응답에 실을 쿠키(지우기). 실을 게 없으면 비어 있음
     */
    public Optional<ResponseCookie> applyOnLogin(HttpServletRequest request, Long userId, boolean guest) {
        if (!previewResultCookie.exists(request)) {
            return Optional.empty();
        }
        Optional<PreviewResult> result = previewResultCookie.read(request);
        if (result.isEmpty()) {
            return Optional.of(previewResultCookie.delete());
        }

        try {
            if (userService.saveBalanceGameIfNotStarted(userId, result.get().type())) {
                log.info("로그인 전 밸런스 게임 결과를 저장했습니다. userId={}, skipped={}", userId, result.get().isSkipped());
            }
        } catch (RuntimeException e) {
            // 로그인은 이미 끝났으므로 막지 않음. 쿠키는 남겨 두어 다음 로그인 때 다시 시도함
            log.warn("로그인 전 밸런스 게임 결과를 저장하지 못했습니다. userId={}", userId, e);
            return Optional.empty();
        }
        return guest ? Optional.empty() : Optional.of(previewResultCookie.delete());
    }
}
