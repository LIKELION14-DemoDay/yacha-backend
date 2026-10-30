package likelion.yacha_backend.domain.adsense.controller;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import likelion.yacha_backend.domain.adsense.dto.AdSenseStatisticsResponse;
import likelion.yacha_backend.domain.adsense.service.AdSenseOAuthException;
import likelion.yacha_backend.domain.adsense.service.AdSenseStatisticsService;
import likelion.yacha_backend.global.response.ApiResponse;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SecurityConfig의 /api/v1/admin/** 규칙으로 ADMIN JWT만 허용된다. */
@Tag(name = "AdSense 관리", description = "관리자용 광고 수익 통계")
@RestController
@RequestMapping("/api/v1/admin/ads")
public class AdSenseStatisticsController {
    private final AdSenseStatisticsService service;

    public AdSenseStatisticsController(AdSenseStatisticsService service) {
        this.service = service;
    }

    @Operation(summary = "AdSense 수익 통계 조회", description = "관리자 JWT가 필요하며 최대 366일 기간의 일별 수익, 페이지뷰, 노출수, 클릭수를 조회합니다.")
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<AdSenseStatisticsResponse>> statistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getStatistics(startDate, endDate)));
    }

    @ExceptionHandler(AdSenseOAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAdSenseException(AdSenseOAuthException e) {
        return ResponseEntity.status(e.status())
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(false, null,
                        new ApiResponse.ErrorDetail("ADSENSE_STATISTICS_ERROR", e.getMessage()),
                        MDC.get("traceId")));
    }
}
