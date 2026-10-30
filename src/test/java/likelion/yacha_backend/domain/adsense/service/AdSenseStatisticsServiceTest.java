package likelion.yacha_backend.domain.adsense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import likelion.yacha_backend.domain.adsense.client.AdSenseReportClient;
import likelion.yacha_backend.domain.adsense.config.AdSenseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AdSenseStatisticsServiceTest {
    @Mock AdSenseCredentialService credentials;
    @Mock AdSenseReportClient client;
    AdSenseStatisticsService service;

    @BeforeEach
    void setUp() {
        var properties = new AdSenseProperties(true, "client", "secret", "http://localhost:8080/callback",
                "accounts/pub-123", "irrelevant-to-this-test", false);
        service = new AdSenseStatisticsService(properties, credentials, client);
    }

    @Test
    void readsMetricsByHeaderNameAndPreservesCurrency() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 2);
        var report = new AdSenseReportClient.ReportResult(
                List.of(
                        header("DATE", null),
                        header("CLICKS", null),
                        header("ESTIMATED_EARNINGS", "USD"),
                        header("PAGE_VIEWS", null),
                        header("IMPRESSIONS", null)),
                List.of(row("2026-10-02", "2", "1.27", "100", "50"),
                        row("2026-10-01", "1", "0.35", "20", "10")),
                row(null, "3", "1.62", "120", "60"),
                List.of("sample warning"));
        when(credentials.getRefreshToken()).thenReturn("refresh");
        when(client.refreshAccessToken("refresh")).thenReturn("access");
        when(client.generateDailyReport("access", start, end)).thenReturn(report);

        var output = service.getStatistics(start, end);
        assertEquals("USD", output.currencyCode());
        assertEquals(120, output.totals().pageViews());
        assertEquals(60, output.totals().impressions());
        assertEquals(3, output.totals().clicks());
        assertEquals(new BigDecimal("1.62"), output.totals().estimatedEarnings());
        assertEquals(start, output.daily().get(0).date());
        assertEquals(new BigDecimal("0.35"), output.daily().get(0).estimatedEarnings());
        assertEquals(List.of("sample warning"), output.warnings());
    }

    @Test
    void emptyRowsWithoutTotalsProduceExplicitZeroSummary() {
        var date = LocalDate.of(2026, 10, 1);
        var report = new AdSenseReportClient.ReportResult(
                List.of(header("DATE", null), header("PAGE_VIEWS", null),
                        header("IMPRESSIONS", null), header("CLICKS", null),
                        header("ESTIMATED_EARNINGS", "KRW")), null, null, null);
        when(credentials.getRefreshToken()).thenReturn("refresh");
        when(client.refreshAccessToken("refresh")).thenReturn("access");
        when(client.generateDailyReport("access", date, date)).thenReturn(report);

        var output = service.getStatistics(date, date);
        assertEquals("KRW", output.currencyCode());
        assertEquals(0, output.totals().pageViews());
        assertEquals(0, output.totals().clicks());
        assertEquals(BigDecimal.ZERO, output.totals().estimatedEarnings());
        assertEquals(List.of(), output.daily());
        assertEquals(List.of(), output.warnings());
    }

    @Test
    void rejectsInvalidDateRangeBeforeAccessingCredentials() {
        var e = assertThrows(AdSenseOAuthException.class, () -> service.getStatistics(
                LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 1)));
        assertEquals(HttpStatus.BAD_REQUEST, e.status());
        verifyNoInteractions(credentials, client);
    }

    @Test
    void rejectsMoreThan366CalendarDays() {
        var start = LocalDate.of(2025, 1, 1);
        var e = assertThrows(AdSenseOAuthException.class,
                () -> service.getStatistics(start, start.plusDays(366)));
        assertEquals(HttpStatus.BAD_REQUEST, e.status());
        verifyNoInteractions(credentials, client);
    }

    @Test
    void missingExpectedMetricFailsInsteadOfReturningInventedNumbers() {
        var date = LocalDate.of(2026, 10, 1);
        var report = new AdSenseReportClient.ReportResult(
                List.of(header("DATE", null), header("PAGE_VIEWS", null)),
                List.of(), null, null);
        when(credentials.getRefreshToken()).thenReturn("refresh");
        when(client.refreshAccessToken("refresh")).thenReturn("access");
        when(client.generateDailyReport("access", date, date)).thenReturn(report);
        var e = assertThrows(AdSenseOAuthException.class, () -> service.getStatistics(date, date));
        assertEquals(HttpStatus.BAD_GATEWAY, e.status());
    }

    private static AdSenseReportClient.Header header(String name, String currency) {
        return new AdSenseReportClient.Header(name, "METRIC_TALLY", currency);
    }

    private static AdSenseReportClient.Row row(String... values) {
        return new AdSenseReportClient.Row(java.util.Arrays.stream(values)
                .map(AdSenseReportClient.Cell::new).toList());
    }
}
