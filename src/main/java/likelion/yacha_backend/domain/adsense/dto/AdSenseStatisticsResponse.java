package likelion.yacha_backend.domain.adsense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 수익은 Google이 반환한 원본 통화 단위의 십진수이며, 환율을 임의로 적용하지 않는다. */
public record AdSenseStatisticsResponse(
        LocalDate startDate,
        LocalDate endDate,
        String currencyCode,
        Totals totals,
        List<Daily> daily,
        List<String> warnings
) {
    public record Totals(long pageViews, long impressions, long clicks, BigDecimal estimatedEarnings) { }
    public record Daily(LocalDate date, long pageViews, long impressions,
                        long clicks, BigDecimal estimatedEarnings) { }
}
