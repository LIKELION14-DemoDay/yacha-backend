package likelion.yacha_backend.domain.adsense.controller;

import java.time.LocalDate;
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
@RestController
@RequestMapping("/api/v1/admin/ads")
public class AdSenseStatisticsController {
    private final AdSenseStatisticsService service;

    public AdSenseStatisticsController(AdSenseStatisticsService service) {
        this.service = service;
    }

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
