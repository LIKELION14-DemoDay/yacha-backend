package likelion.yacha_backend.domain.philosophy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyBookmarkResponse;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyCardsResponse;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyDetailResponse;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyMemoRequest;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyMemoResponse;
import likelion.yacha_backend.domain.philosophy.dto.ThisWeekPhilosophyResponse;
import likelion.yacha_backend.domain.philosophy.service.PhilosophyBookmarkService;
import likelion.yacha_backend.domain.philosophy.service.PhilosophyMemoService;
import likelion.yacha_backend.domain.philosophy.service.PhilosophyService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1분 철학. 회원 전용
 * {@code /philosophy/**}는 SecurityConfig의 게스트 허용 목록에 없어 게스트는 403 {@code GUEST_NOT_ALLOWED}
 */
@Tag(name = "1분 철학", description = "주마다 공개되는 주제별 철학자 글 · 메모 · 북마크 (회원 전용)")
@RestController
@RequestMapping("/api/v1/philosophy")
@RequiredArgsConstructor
public class PhilosophyController {

    private final PhilosophyService philosophyService;
    private final PhilosophyMemoService memoService;
    private final PhilosophyBookmarkService bookmarkService;

    @Operation(
            summary = "이번 주 글 목록",
            description = """
                    1분 철학 첫 화면. 이번 주에 공개된 글 카드 (보통 5개), 만든 순서대로
                    매주 월요일에 새 글로 바뀜. 새 글이 아직 공개되지 않았으면 지난주 글이 그대로 보임

                    카드: 해시태그(`topic.categoryName` 또는 `topic.subcategoryName`) · 질문(`topic.statement`) · 철학자 이름(`philosophers`) · 북마크 여부
                    카드를 누르면 `GET /philosophy/{id}`

                    공개된 글이 하나도 없으면 `weekStart`가 null이고 `articles`가 빈 목록

                    회원 전용. 게스트는 `GUEST_NOT_ALLOWED` (403)
                    """)
    @GetMapping("/this-week")
    public ApiResponse<ThisWeekPhilosophyResponse> getThisWeek(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(philosophyService.getThisWeek(authUser.getUserId()));
    }

    @Operation(
            summary = "지난 글 목록",
            description = """
                    "지난 글 보기". 이번 주보다 앞선 주의 글 카드, 최신 주부터 한 페이지 20개씩 (`page`는 0부터)
                    카드의 `weekStart`(그 주 월요일)로 주별로 묶어 보여주면 됨. `hasNext`가 true면 다음 페이지가 있음

                    회원 전용. `page`가 음수면 `VALIDATION_FAILED` (400)
                    """)
    @GetMapping("/past")
    public ApiResponse<PhilosophyCardsResponse> getPast(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(philosophyService.getPast(authUser.getUserId(), page));
    }

    @Operation(
            summary = "글 상세",
            description = """
                    해시태그 · 질문, 철학자 탭(`philosophers[].name`)과 탭마다 소제목 + 내용(`sections`), 내 메모, 북마크 여부
                    철학자 글은 AI가 정리한 글이라 화면에 그렇게 표시해 주세요

                    에러
                    - `ARTICLE_NOT_FOUND` (404): 없는 글이거나 아직 공개되지 않은 글
                    """)
    @GetMapping("/{id}")
    public ApiResponse<PhilosophyDetailResponse> getDetail(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.success(philosophyService.getDetail(authUser.getUserId(), id));
    }

    @Operation(
            summary = "메모 저장",
            description = """
                    상세 화면의 MEMO. 글마다 하나이고, 다시 저장하면 덮어씀
                    앞뒤 공백은 지우고 저장. 빈 문자열(공백만 있어도)이면 메모를 지움 → 응답의 `memo`가 null
                    응답의 `memo`는 실제로 저장된 내용

                    에러
                    - `VALIDATION_FAILED` (400): `content`가 없거나 1000자 초과
                    - `ARTICLE_NOT_FOUND` (404): 없는 글이거나 아직 공개되지 않은 글
                    """)
    @PutMapping("/{id}/memo")
    public ApiResponse<PhilosophyMemoResponse> saveMemo(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody PhilosophyMemoRequest request) {
        return ApiResponse.success(memoService.save(authUser.getUserId(), id, request.content()));
    }

    @Operation(
            summary = "북마크 추가",
            description = """
                    상세 화면 우측 상단. 이미 돼 있어도 200 (여러 번 불러도 같음)

                    에러
                    - `ARTICLE_NOT_FOUND` (404): 없는 글이거나 아직 공개되지 않은 글
                    """)
    @PutMapping("/{id}/bookmark")
    public ApiResponse<PhilosophyBookmarkResponse> addBookmark(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.success(bookmarkService.add(authUser.getUserId(), id));
    }

    @Operation(
            summary = "북마크 해제",
            description = """
                    안 돼 있어도 200 (여러 번 불러도 같음)

                    에러
                    - `ARTICLE_NOT_FOUND` (404): 없는 글이거나 아직 공개되지 않은 글
                    """)
    @DeleteMapping("/{id}/bookmark")
    public ApiResponse<PhilosophyBookmarkResponse> removeBookmark(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.success(bookmarkService.remove(authUser.getUserId(), id));
    }

    @Operation(
            summary = "내 북마크 목록",
            description = """
                    마이페이지. 북마크한 글 카드, 북마크한 순서 최신부터 한 페이지 20개씩 (`page`는 0부터)
                    카드 모양은 이번 주 · 지난 글과 같음. 누르면 `GET /philosophy/{id}`
                    `hasNext`가 true면 다음 페이지가 있음

                    회원 전용. `page`가 음수면 `VALIDATION_FAILED` (400)
                    """)
    @GetMapping("/bookmarks")
    public ApiResponse<PhilosophyCardsResponse> getMyBookmarks(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(bookmarkService.getMine(authUser.getUserId(), page));
    }
}
