package likelion.yacha_backend.domain.adsense.client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import likelion.yacha_backend.domain.adsense.config.AdSenseProperties;
import likelion.yacha_backend.domain.adsense.service.AdSenseOAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** AdSense 읽기 전용 통계 조회. Refresh Token과 Access Token은 로그/응답에 쓰지 않는다. */
@Component
public class AdSenseReportClient {
    private static final Logger log = LoggerFactory.getLogger(AdSenseReportClient.class);
    // Only log Google error identifiers, never credentials, tokens or response bodies.
    private static final Pattern GOOGLE_ERROR_PATTERN =
            Pattern.compile("\"(?:error|status)\"\\s*:\\s*\"([A-Za-z0-9_-]{1,64})\"");

    @Value("${adsense.site-domain:yachapan.cloud}")
    private String siteDomain = "yachapan.cloud";

    private final RestClient restClient;
    private final AdSenseProperties properties;

    public AdSenseReportClient(@Qualifier("adsenseGoogleRestClient") RestClient restClient,
                               AdSenseProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** 저장된 Refresh Token으로 짧게 유효한 Google Access Token을 발급받는다. */
    public String refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        try {
            Map<?, ?> result = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object token = result == null ? null : result.get("access_token");
            if (!(token instanceof String value) || !StringUtils.hasText(value)) {
                throw new AdSenseOAuthException(HttpStatus.BAD_GATEWAY,
                        "Google이 AdSense Access Token을 반환하지 않았습니다.");
            }
            return value;
        } catch (RestClientResponseException e) {
            log.warn("AdSense token refresh failed: httpStatus={}, googleError={}",
                    e.getStatusCode().value(), googleErrorCode(e));
            if (e.getStatusCode().is4xxClientError()) {
                throw new AdSenseOAuthException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AdSense 인증을 갱신할 수 없습니다. 관리자 OAuth 연결을 다시 확인해 주세요.");
            }
            throw new AdSenseOAuthException(HttpStatus.BAD_GATEWAY,
                    "Google 인증 서버가 정상적으로 응답하지 않았습니다.");
        } catch (ResourceAccessException e) {
            log.warn("AdSense token refresh connection failed: exceptionType={}",
                    e.getClass().getSimpleName());
            throw new AdSenseOAuthException(HttpStatus.BAD_GATEWAY,
                    "Google 인증 서버에 연결할 수 없습니다.");
        } catch (RestClientException e) {
            log.warn("AdSense token refresh response failed: exceptionType={}",
                    e.getClass().getSimpleName());
            throw new AdSenseOAuthException(HttpStatus.BAD_GATEWAY,
                    "Google 인증 응답을 처리할 수 없습니다.");
        }
    }

    /** 계정의 기본 보고 시간대와 기본 통화를 사용한다. 날짜 양 끝은 모두 포함한다. */
    public ReportResult generateDailyReport(String accessToken, LocalDate startDate, LocalDate endDate) {
        String accountName = properties.expectedAccountName();
        if (!StringUtils.hasText(accountName) || !accountName.matches("accounts/pub-[0-9]+")) {
            throw new AdSenseOAuthException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AdSense 계정 리소스 이름 설정을 확인해 주세요.");
        }

        URI uri = UriComponentsBuilder.fromUriString("https://adsense.googleapis.com")
                .path("/v2/")
                .path(accountName)
                .path("/reports:generate")
                .queryParam("dateRange", "CUSTOM")
                .queryParam("startDate.year", startDate.getYear())
                .queryParam("startDate.month", startDate.getMonthValue())
                .queryParam("startDate.day", startDate.getDayOfMonth())
                .queryParam("endDate.year", endDate.getYear())
                .queryParam("endDate.month", endDate.getMonthValue())
                .queryParam("endDate.day", endDate.getDayOfMonth())
                .queryParam("dimensions", "DATE")
                .queryParam("filters", "OWNED_SITE_DOMAIN_NAME==" + siteDomain)
                .queryParam("metrics", "PAGE_VIEWS")
                .queryParam("metrics", "IMPRESSIONS")
                .queryParam("metrics", "CLICKS")
                .queryParam("metrics", "ESTIMATED_EARNINGS")
                .queryParam("limit", 366)
                .build().encode().toUri();

        try {
            ReportResult result = restClient.get()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(ReportResult.class);
            if (result == null) {
                throw new AdSenseOAuthException(HttpStatus.BAD_GATEWAY,
                        "AdSense 보고서가 비어 있는 응답을 반환했습니다.");
            }
            return result;
        } catch (RestClientResponseException e) {
            log.warn("AdSense report request failed: httpStatus={}, googleError={}",
                    e.getStatusCode().value(), googleErrorCode(e));
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new AdSenseOAuthException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AdSense 보고서 접근 권한을 확인할 수 없습니다. 관리자 OAuth 재연결이 필요할 수 있습니다.");
            }
            if (e.getStatusCode().value() == 429) {
                throw new AdSenseOAuthException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AdSense 요청 제한에 도달했습니다. 잠시 뒤 다시 조회해 주세요.");
            }
            throw new AdSenseOAuthException(HttpStatus.BAD_GATEWAY,
                    "AdSense 보고서 조회에 실패했습니다.");
        } catch (ResourceAccessException e) {
            log.warn("AdSense report connection failed: exceptionType={}",
                    e.getClass().getSimpleName());
            throw new AdSenseOAuthException(HttpStatus.BAD_GATEWAY,
                    "AdSense 보고서 서버에 연결할 수 없습니다.");
        } catch (RestClientException e) {
            log.warn("AdSense report response failed: exceptionType={}",
                    e.getClass().getSimpleName());
            throw new AdSenseOAuthException(HttpStatus.BAD_GATEWAY,
                    "AdSense 보고서 응답을 처리할 수 없습니다.");
        }
    }

    private static String googleErrorCode(RestClientResponseException exception) {
        try {
            Matcher matcher = GOOGLE_ERROR_PATTERN.matcher(
                    exception.getResponseBodyAsString(StandardCharsets.UTF_8));
            return matcher.find() ? matcher.group(1) : "unknown";
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    // Google API v2 ReportResult JSON의 필요한 필드만 역직렬화한다.
    public record ReportResult(List<Header> headers, List<Row> rows, Row totals, List<String> warnings) { }
    public record Header(String name, String type, String currencyCode) { }
    public record Row(List<Cell> cells) { }
    public record Cell(String value) { }
}
