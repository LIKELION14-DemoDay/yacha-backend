package likelion.yacha_backend.domain.philosophy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import likelion.yacha_backend.domain.philosophy.dto.AdminArticleResponse;
import likelion.yacha_backend.domain.philosophy.dto.AdminGenerationResponse;
import likelion.yacha_backend.domain.philosophy.service.PhilosophyAdminService;
import likelion.yacha_backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1분 철학 검토 · 승인
 * {@code /api/v1/admin/**}는 SecurityConfig에서 ADMIN만 들어올 수 있음
 */
@Tag(name = "관리자 · 1분 철학", description = "AI가 만든 1분 철학 글 검토 · 승인 · 다시 생성 (ADMIN 전용)")
@RestController
@RequestMapping("/api/v1/admin/philosophy")
@RequiredArgsConstructor
public class PhilosophyAdminController {

    private final PhilosophyAdminService adminService;

    @Operation(
            summary = "주별 글 목록 (검토용)",
            description = """
                    그 주의 글 전체를 철학자 글 내용까지 보여줌. 검토 전(`PENDING`)과 승인됨(`APPROVED`) 모두
                    `weekStart`를 빼면 다음 주

                    매주 목요일 10시에 다음 주 글이 자동으로 만들어짐 → 여기서 읽어 보고 승인
                    - 괜찮으면 승인 (`POST /{id}/approve`)
                    - 내용이 이상하면 다시 생성 (`POST /{id}/regenerate`)
                    - 주제가 1분 철학에 맞지 않으면 빼고 (`DELETE /{id}`) 모자란 만큼 생성 (`POST /generate`)

                    에러
                    - `INVALID_WEEK_START` (400): 월요일이 아님
                    """)
    @GetMapping
    public ApiResponse<List<AdminArticleResponse>> getWeek(
            @Parameter(description = "주의 월요일 (예: 2026-11-02). 빼면 다음 주")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return ApiResponse.success(adminService.getWeek(adminService.weekStartOrNext(weekStart)));
    }

    @Operation(
            summary = "승인",
            description = """
                    승인한 글은 그 주 월요일부터 사용자에게 보임. 이미 승인된 글이면 그대로

                    에러
                    - `ARTICLE_NOT_FOUND` (404)
                    """)
    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        adminService.approve(id);
        return ApiResponse.noContent();
    }

    @Operation(
            summary = "다시 생성",
            description = """
                    같은 주제로 AI 글을 다시 만들고 검토 전으로 되돌림 (승인돼 있었어도)
                    오래 걸려서 바로 202로 응답하고 뒤에서 만듦. 1~2분 뒤 목록에서 확인
                    새 글이 만들어진 뒤에 바뀌므로, 실패하면 이전 글이 그대로 남음 (실패는 서버 로그)

                    에러
                    - `ARTICLE_NOT_FOUND` (404)
                    - `ARTICLE_ALREADY_PUBLISHED` (409): 이미 공개된 글 (승인됐고 그 주가 시작됨)
                    - `GENERATION_IN_PROGRESS` (409): 다른 생성이 돌고 있음
                    """)
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<ApiResponse<Void>> regenerate(@PathVariable Long id) {
        adminService.startRegenerate(id);
        return ResponseEntity.accepted().body(ApiResponse.noContent());
    }

    @Operation(
            summary = "빼기",
            description = """
                    주제째 뺌. 모자란 편은 `POST /generate`로 다른 주제를 채움

                    에러
                    - `ARTICLE_NOT_FOUND` (404)
                    - `ARTICLE_ALREADY_PUBLISHED` (409): 이미 공개된 글
                    """)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        return ApiResponse.noContent();
    }

    @Operation(
            summary = "모자란 만큼 생성",
            description = """
                    그 주가 정해진 편수(5편)가 되도록 모자란 만큼 주제를 골라 AI 글을 만듦. `weekStart`를 빼면 다음 주
                    스케줄러가 실패했을 때 · 뺀 주제를 채울 때 · 런칭 첫 주(이번 주)에 씀
                    오래 걸려서 바로 202로 응답하고 뒤에서 만듦. 몇 분 뒤 목록에서 확인 (실패는 서버 로그)

                    에러
                    - `INVALID_WEEK_START` (400): 월요일이 아님
                    - `PAST_WEEK` (400): 이번 주보다 앞선 주
                    - `WEEK_ALREADY_FULL` (409): 이미 다 있음
                    - `GENERATION_IN_PROGRESS` (409): 다른 생성이 돌고 있음
                    """)
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AdminGenerationResponse>> generate(
            @Parameter(description = "주의 월요일 (예: 2026-11-02). 빼면 다음 주")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        LocalDate target = adminService.weekStartOrNext(weekStart);
        adminService.startGenerate(target);
        return ResponseEntity.accepted().body(ApiResponse.success(new AdminGenerationResponse(target)));
    }
}
