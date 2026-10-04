package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("GameProperties — game.* 설정 검증")
class GamePropertiesTest {

    @Configuration
    @EnableConfigurationProperties(GameProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    @DisplayName("값이 모두 양수면 기동한다")
    void valid() {
        runner.withPropertyValues("game.chat-max-length=100", "game.max-chats-per-participant=200",
                        "game.argument-max-length=200", "game.rebuttal-max-length=250")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("값이 빠지면(0 으로 바인딩) 기동이 실패한다")
    void missing() {
        runner.withPropertyValues("game.chat-max-length=100")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("반론 글자 수 상한이 빠지면 기동이 실패한다")
    void missingRebuttalMaxLength() {
        runner.withPropertyValues("game.chat-max-length=100", "game.max-chats-per-participant=200",
                        "game.argument-max-length=200")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("키에 오타가 나도 기동이 실패한다")
    void typo() {
        runner.withPropertyValues("game.chat-max-length=100", "game.max-chat-per-participant=200",
                        "game.argument-max-length=200", "game.rebuttal-max-length=250")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("0 이하면 기동이 실패한다")
    void notPositive() {
        runner.withPropertyValues("game.chat-max-length=0", "game.max-chats-per-participant=200",
                        "game.argument-max-length=200", "game.rebuttal-max-length=250")
                .run(context -> assertThat(context).hasFailed());
    }
}
