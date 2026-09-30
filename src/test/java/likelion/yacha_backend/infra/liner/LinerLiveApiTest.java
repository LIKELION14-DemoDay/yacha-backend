package likelion.yacha_backend.infra.liner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

@DisplayName("LINER 실제 API 연동")
class LinerLiveApiTest {

    private static final String BASE_URL = "https://platform.liner.com";

    @Test
    @DisplayName("실제 LINER Web Search API로 한글 검색이 가능하다")
    @EnabledIfEnvironmentVariable(
            named = "LINER_LIVE_TEST",
            matches = "true"
    )
    void searchKoreanQuery() {

        String apiKey = System.getenv("LINER_API_KEY");

        LinerProperties properties =
                new LinerProperties(BASE_URL, apiKey);

        LinerClient client = new LinerClient(
                properties,
                RestClient.builder()
                        .baseUrl(BASE_URL)
        );

        LinerSearchResponse response =
                client.searchWeb(
                        "친구의 범죄를 알게 되었을 때 신고해야 하는 근거",
                        3
                );

        assertThat(response).isNotNull();
        assertThat(response.results()).isNotNull();
        assertThat(response.results()).isNotEmpty();

        response.results().forEach(result -> {
            System.out.println("제목: " + result.title());
            System.out.println("URL: " + result.url());
            System.out.println("설명: " + result.description());
            System.out.println("--------------------");
        });
    }
}