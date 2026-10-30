package likelion.yacha_backend.domain.adsense.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import likelion.yacha_backend.domain.adsense.client.AdSenseReportClient;
import likelion.yacha_backend.domain.adsense.config.AdSenseProperties;
import likelion.yacha_backend.domain.adsense.dto.AdSenseStatisticsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdSenseStatisticsService {
    private static final int MAX_DAYS = 366;

    private final AdSenseProperties properties;
    private final AdSenseCredentialService credentials;
    private final AdSenseReportClient reportClient;

    public AdSenseStatisticsService(AdSenseProperties properties, AdSenseCredentialService credentials,
                                    AdSenseReportClient reportClient) {
        this.properties = properties;
        this.credentials = credentials;
        this.reportClient = reportClient;
    }

    public AdSenseStatisticsResponse getStatistics(LocalDate startDate, LocalDate endDate) {
        validate(startDate, endDate);
        // 아직 OAuth를 완료하지 않았다면 여기서 503. 실제 수익을 임의로 0원으로 만들지 않는다.
        String refreshToken = credentials.getRefreshToken();
        String accessToken = reportClient.refreshAccessToken(refreshToken);
        AdSenseReportClient.ReportResult report = reportClient.generateDailyReport(accessToken, startDate, endDate);
        return convert(startDate, endDate, report);
    }

    private void validate(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)
                || ChronoUnit.DAYS.between(startDate, endDate) >= MAX_DAYS) {
            throw new AdSenseOAuthException(HttpStatus.BAD_REQUEST,
                    "조회 기간은 시작일이 종료일보다 늦지 않은 최대 366일이어야 합니다.");
        }
        if (!properties.enabled() || !StringUtils.hasText(properties.clientId())
                || !StringUtils.hasText(properties.clientSecret())
                || !StringUtils.hasText(properties.expectedAccountName())) {
            throw new AdSenseOAuthException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AdSense 통계 조회 환경변수가 설정되지 않았습니다.");
        }
    }

    private AdSenseStatisticsResponse convert(LocalDate start, LocalDate end,
                                              AdSenseReportClient.ReportResult report) {
        if (report.headers() == null) {
            throw badReport();
        }
        Map<String, Integer> column = new HashMap<>();
        for (int i = 0; i < report.headers().size(); i++) {
            AdSenseReportClient.Header header = report.headers().get(i);
            if (header != null && header.name() != null) column.put(header.name(), i);
        }
        for (String required : List.of("DATE", "PAGE_VIEWS", "IMPRESSIONS", "CLICKS", "ESTIMATED_EARNINGS")) {
            if (!column.containsKey(required)) throw badReport();
        }
        String currency = report.headers().get(column.get("ESTIMATED_EARNINGS")).currencyCode();
        List<AdSenseStatisticsResponse.Daily> daily = new ArrayList<>();
        List<AdSenseReportClient.Row> rows = report.rows() == null ? List.of() : report.rows();
        for (AdSenseReportClient.Row row : rows) {
            try {
                LocalDate date = LocalDate.parse(cell(row, column.get("DATE")));
                if (date.isBefore(start) || date.isAfter(end)) throw badReport();
                daily.add(new AdSenseStatisticsResponse.Daily(date,
                        number(row, column.get("PAGE_VIEWS")),
                        number(row, column.get("IMPRESSIONS")),
                        number(row, column.get("CLICKS")),
                        money(row, column.get("ESTIMATED_EARNINGS"))));
            } catch (RuntimeException e) {
                throw badReport();
            }
        }
        daily.sort(java.util.Comparator.comparing(AdSenseStatisticsResponse.Daily::date));

        // Google의 totals를 우선 사용. totals가 없으면 일별 행의 합계로 계산한다.
        AdSenseStatisticsResponse.Totals totals;
        if (report.totals() != null) {
            try {
                totals = new AdSenseStatisticsResponse.Totals(
                        number(report.totals(), column.get("PAGE_VIEWS")),
                        number(report.totals(), column.get("IMPRESSIONS")),
                        number(report.totals(), column.get("CLICKS")),
                        money(report.totals(), column.get("ESTIMATED_EARNINGS")));
            } catch (RuntimeException e) {
                throw badReport();
            }
        } else {
            totals = new AdSenseStatisticsResponse.Totals(
                    daily.stream().mapToLong(AdSenseStatisticsResponse.Daily::pageViews).sum(),
                    daily.stream().mapToLong(AdSenseStatisticsResponse.Daily::impressions).sum(),
                    daily.stream().mapToLong(AdSenseStatisticsResponse.Daily::clicks).sum(),
                    daily.stream().map(AdSenseStatisticsResponse.Daily::estimatedEarnings)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        List<String> warnings = report.warnings() == null ? List.of() : List.copyOf(report.warnings());
        return new AdSenseStatisticsResponse(start, end, currency, totals, List.copyOf(daily), warnings);
    }

    private static String cell(AdSenseReportClient.Row row, int position) {
        if (row == null || row.cells() == null || position >= row.cells().size()
                || row.cells().get(position) == null) throw badReport();
        String value = row.cells().get(position).value();
        if (value == null || value.isBlank()) throw badReport();
        return value;
    }

    private static long number(AdSenseReportClient.Row row, int position) {
        try {
            return Long.parseLong(cell(row, position));
        } catch (NumberFormatException e) {
            throw badReport();
        }
    }

    private static BigDecimal money(AdSenseReportClient.Row row, int position) {
        try {
            return new BigDecimal(cell(row, position));
        } catch (NumberFormatException e) {
            throw badReport();
        }
    }

    private static AdSenseOAuthException badReport() {
        return new AdSenseOAuthException(HttpStatus.BAD_GATEWAY,
                "AdSense 보고서 데이터 형식이 예상과 다릅니다.");
    }
}
