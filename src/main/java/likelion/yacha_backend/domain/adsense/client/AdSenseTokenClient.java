
package likelion.yacha_backend.domain.adsense.client;

import java.util.List;
import java.util.Map;

import likelion.yacha_backend.domain.adsense.config.AdSenseProperties;
import likelion.yacha_backend.domain.adsense.service.AdSenseOAuthException;

import org.springframework.beans.factory.annotation.Qualifier;
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

@Component
public class AdSenseTokenClient {

    private final RestClient client;
    private final AdSenseProperties properties;

    public AdSenseTokenClient(
            @Qualifier("adsenseGoogleRestClient") RestClient client,
            AdSenseProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    public TokenResult exchangeCode(String authorizationCode) {
        MultiValueMap<String, String> body =
                new LinkedMultiValueMap<>();

        body.add("code", authorizationCode);
        body.add("client_id", properties.clientId());
        body.add("client_secret", properties.clientSecret());
        body.add("redirect_uri", properties.redirectUri());
        body.add("grant_type", "authorization_code");

        try {
            Map<?, ?> payload = client.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (payload == null
                    || !(payload.get("access_token") instanceof String accessToken)
                    || !StringUtils.hasText(accessToken)
                    || !(payload.get("refresh_token") instanceof String refreshToken)
                    || !StringUtils.hasText(refreshToken)) {

                throw new AdSenseOAuthException(
                        HttpStatus.BAD_GATEWAY,
                        "Google이 Access Token 또는 Refresh Token을 반환하지 않았습니다."
                );
            }

            return new TokenResult(
                    accessToken,
                    refreshToken
            );

        } catch (RestClientResponseException e) {
            throw new AdSenseOAuthException(
                    e.getStatusCode().is4xxClientError()
                            ? HttpStatus.BAD_REQUEST
                            : HttpStatus.BAD_GATEWAY,
                    "Google OAuth 인증 코드를 교환하지 못했습니다."
            );

        } catch (ResourceAccessException e) {
            throw new AdSenseOAuthException(
                    HttpStatus.BAD_GATEWAY,
                    "Google 인증 서버에 연결하지 못했습니다."
            );

        } catch (RestClientException e) {
            throw new AdSenseOAuthException(
                    HttpStatus.BAD_GATEWAY,
                    "Google OAuth 인증 응답을 처리하지 못했습니다."
            );
        }
    }

    public void verifyExpectedAccount(String accessToken) {
        try {
            Map<?, ?> payload = client.get()
                    .uri("https://adsense.googleapis.com/v2/accounts")
                    .headers(headers ->
                            headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(Map.class);

            Object rawAccounts = payload == null
                    ? null
                    : payload.get("accounts");

            boolean found =
                    rawAccounts instanceof List<?> accounts
                            && accounts.stream().anyMatch(
                            item -> item instanceof Map<?, ?> account
                                    && properties.expectedAccountName()
                                    .equals(account.get("name"))
                    );

            if (!found) {
                throw new AdSenseOAuthException(
                        HttpStatus.FORBIDDEN,
                        "승인한 Google 계정에서 야차판 AdSense 계정을 찾지 못했습니다."
                );
            }

        } catch (RestClientResponseException
                 | ResourceAccessException e) {

            throw new AdSenseOAuthException(
                    HttpStatus.BAD_GATEWAY,
                    "AdSense 계정 확인에 실패했습니다."
            );

        } catch (RestClientException e) {
            throw new AdSenseOAuthException(
                    HttpStatus.BAD_GATEWAY,
                    "Google AdSense 계정 응답을 처리하지 못했습니다."
            );
        }
    }

    public record TokenResult(
            String accessToken,
            String refreshToken
    ) {
    }
}
