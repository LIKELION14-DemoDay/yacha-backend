package likelion.yacha_backend.domain.balancegame.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import likelion.yacha_backend.domain.balancegame.dto.BalanceGameAnswerRequest;
import likelion.yacha_backend.domain.balancegame.dto.PhilosopherResponse;
import likelion.yacha_backend.domain.balancegame.dto.QuestionResponse;
import likelion.yacha_backend.domain.balancegame.service.BalanceGameService;
import likelion.yacha_backend.domain.balancegame.service.PreviewResult;
import likelion.yacha_backend.domain.balancegame.service.PreviewResultCookie;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "밸런스 게임", description = "질문 12개로 철학 유형 정하기 · 건너뛰기 · 로그인 전 미리보기 · 유형 설명(공유)")
@RestController
@RequestMapping("/api/v1/balance-game")
@RequiredArgsConstructor
public class BalanceGameController {

    private final BalanceGameService balanceGameService;
    private final PreviewResultCookie previewResultCookie;

    @Operation(
            summary = "질문 12개",
            description = """
                    밸런스 게임 질문을 순서대로 줌. `optionA` 는 왼쪽 카드, `optionB` 는 오른쪽 카드 (배점은 주지 않음)
                    로그인 전 온보딩에서 쓰고, 홈에 들어왔을 때 `GET /users/me` 의 `balanceGameRequired` 가 true 면 이 화면을 띄움

                    로그인 없이 호출 가능
                    """)
    @SecurityRequirements   // 전역 bearerAuth를 끔. 인증 없이 호출
    @GetMapping("/questions")
    public ApiResponse<List<QuestionResponse>> questions() {
        return ApiResponse.success(balanceGameService.questions());
    }

    @Operation(
            summary = "결과 제출",
            description = """
                    12문항의 답을 질문 순서대로 `A` / `B` 로 보내면 철학 유형을 정해 저장하고 결과 화면 내용을 줌
                    로그인한 뒤 홈 팝업 · 마이페이지 "밸런스 게임 시작하기" 에서 씀. 로그인 전 온보딩에서는 `/preview`
                    다시 하면 새 결과로 덮어씀. 중간 답은 저장하지 않으므로 도중에 나가면 처음부터
                    중간의 "그만하기" 는 건너뛰기와 같음 (`/skip`)

                    동점이면 +2 를 더 많이 받은 철학자, 그래도 같으면 가장 나중 질문에서 점수를 받은 철학자 (같은 답이면 항상 같은 결과)
                    `sections` 는 소제목(`title`)과 본문(`content`) 3개. 소제목은 앞에 "■ " 가 붙어 있고 유형마다 다름 (니체: ■ 나만의 기준 · ■ 성장을 위한 도전 · ■ 다름을 선택하는 용기)

                    로그인 필요 (게스트 가능, 가입해도 유형은 그대로)
                    답이 12개가 아니면 `VALIDATION_FAILED`, `A` · `B` 가 아닌 값이면 `PARSING_ERROR`
                    """)
    @PostMapping("/result")
    public ApiResponse<PhilosopherResponse> submit(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody BalanceGameAnswerRequest request) {
        return ApiResponse.success(balanceGameService.submit(authUser.getUserId(), request));
    }

    @Operation(
            summary = "결과 미리보기 (로그인 전 온보딩)",
            description = """
                    로그인 전에 답 12개로 결과 화면 내용을 줌. 응답은 결과 제출과 같음
                    저장하지 않고 결과를 쿠키(`balanceGamePreview`, HttpOnly, 1시간)로 내려 둠
                    로그인 · 회원가입 · 비회원 시작 · 소셜 로그인 때 서버가 이 쿠키를 보고 계정에 저장하므로, 프론트는 따로 저장할 필요 없음
                    다시 하면 새 결과로 덮어씀

                    로그인 없이 호출 가능. 쿠키가 실리도록 `credentials: 'include'` 필요
                    답이 12개가 아니면 `VALIDATION_FAILED`, `A` · `B` 가 아닌 값이면 `PARSING_ERROR` (쿠키는 내려가지 않음)
                    """)
    @SecurityRequirements   // 전역 bearerAuth를 끔. 인증 없이 호출
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<PhilosopherResponse>> preview(@Valid @RequestBody BalanceGameAnswerRequest request) {
        PhilosopherResponse response = balanceGameService.preview(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, previewResultCookie.create(PreviewResult.of(response.type())).toString())
                .body(ApiResponse.success(response));
    }

    @Operation(
            summary = "건너뛰기 미리보기 (로그인 전 온보딩)",
            description = """
                    온보딩의 "건너뛰기" · 중간의 "그만하기". 저장하지 않고 건너뜀을 쿠키(`balanceGamePreview`, 1시간)로 내려 둠
                    온보딩 결과 화면에는 다시 하기가 없음. 다시 하기는 로그인 후 마이페이지에서 하고(`/result` · `/skip`), 그때 그만두면 이전 유형을 그대로 둠
                    이미 결과를 받았으면(`/preview` 를 부른 뒤) 부르지 않음. 부르면 받은 결과가 건너뜀으로 덮어써짐
                    로그인할 때 서버가 이 쿠키를 보고 건너뜀으로 저장하므로, 홈에서 다시 띄우지 않음

                    로그인 없이 호출 가능. 쿠키가 실리도록 `credentials: 'include'` 필요
                    """)
    @SecurityRequirements   // 전역 bearerAuth를 끔. 인증 없이 호출
    @PostMapping("/preview/skip")
    public ResponseEntity<ApiResponse<Void>> previewSkip() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, previewResultCookie.create(PreviewResult.skipped()).toString())
                .body(ApiResponse.noContent());
    }

    @Operation(
            summary = "건너뛰기",
            description = """
                    로그인한 뒤 홈 팝업 · 마이페이지에서 하는 밸런스 게임의 "건너뛰기" 와 중간의 "그만하기". 로그인 전 온보딩에서는 `/preview/skip`
                    바로 홈으로 가고, 다음부터 홈에서 띄우지 않음 (마이페이지에서만 시작)
                    유형이 없으면 "일반인" 으로 표시됨. 이미 결과가 있는 사람이 다시 하다 그만두면 이전 유형을 그대로 둠

                    로그인 필요 (게스트 가능)
                    """)
    @PostMapping("/skip")
    public ApiResponse<Void> skip(@AuthenticationPrincipal AuthUser authUser) {
        balanceGameService.skip(authUser.getUserId());
        return ApiResponse.noContent();
    }

    @Operation(
            summary = "유형 설명 (공유)",
            description = """
                    결과 화면 "링크 복사" 로 받은 친구가 보는 유형 설명. 결과 제출 응답과 같은 형식이고 누구의 결과인지는 담지 않음
                    `type` 은 `KANT` · `HOBBES` · `SARTRE` · `ROUSSEAU` · `MILL` · `EPICTETUS` · `NIETZSCHE` · `EPICURUS`

                    로그인 없이 호출 가능
                    없는 유형이면 `BINDING_ERROR`
                    """)
    @GetMapping("/philosophers/{type}")
    public ApiResponse<PhilosopherResponse> describe(@PathVariable PhilosopherType type) {
        return ApiResponse.success(balanceGameService.describe(type));
    }
}
