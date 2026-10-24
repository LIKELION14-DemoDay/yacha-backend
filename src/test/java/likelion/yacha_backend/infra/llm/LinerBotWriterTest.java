
package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import likelion.yacha_backend.domain.ai.AiMessage;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.session.entity.Stance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LinerBotWriter 단위 테스트")
class LinerBotWriterTest {

    private static final AiTopic TOPIC = new AiTopic(
            "AI가 인간의 일자리를 대체하는 것은 바람직한가?",
            "AI가 인간의 일자리를 대체하는 것은 바람직하다",
            "AI가 인간의 일자리를 대체하는 것은 바람직하지 않다"
    );

    @Test
    @DisplayName("AI 주장은 주제와 입장을 전달하고 200자로 제한한다")
    void writeArgument() {
        AtomicReference<String> capturedInput = new AtomicReference<>();
        AtomicReference<String> capturedLabel = new AtomicReference<>();

        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> {
                    capturedInput.set(input);
                    capturedLabel.set(label);
                    return "가".repeat(210);
                },
                (system, input, label) -> "사용하지 않음"
        );

        String result = writer.writeArgument(
                TOPIC,
                Stance.DISAGREE
        );

        assertThat(result).hasSize(200);

        assertThat(capturedInput.get())
                .contains(TOPIC.statement())
                .contains(TOPIC.disagreeText());

        assertThat(capturedLabel.get())
                .isEqualTo("bot-argument");
    }

    @Test
    @DisplayName("AI 반론은 상대방의 주장을 참고한다")
    void writeRebuttal() {
        AtomicReference<String> capturedInput = new AtomicReference<>();

        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> {
                    capturedInput.set(input);
                    return "상대방 주장의 근거가 충분하지 않습니다.";
                },
                (system, input, label) -> "사용하지 않음"
        );

        String opponentArgument =
                "AI 기술 발전으로 새로운 일자리가 생길 수 있습니다.";

        String result = writer.writeRebuttal(
                TOPIC,
                Stance.DISAGREE,
                opponentArgument
        );

        assertThat(result)
                .isEqualTo("상대방 주장의 근거가 충분하지 않습니다.");

        assertThat(capturedInput.get())
                .contains(TOPIC.statement())
                .contains(TOPIC.disagreeText())
                .contains(opponentArgument);
    }

    @Test
    @DisplayName("AI 반론은 최대 250자로 제한한다")
    void rebuttalLengthLimit() {
        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "가".repeat(300),
                (system, input, label) -> "사용하지 않음"
        );

        String result = writer.writeRebuttal(
                TOPIC,
                Stance.AGREE,
                "상대방 주장"
        );

        assertThat(result).hasSize(250);
    }

    @Test
    @DisplayName("AI 채팅은 기존 대화를 참고하고 50자로 제한한다")
    void reply() {
        AtomicReference<String> capturedInput = new AtomicReference<>();
        AtomicReference<String> capturedLabel = new AtomicReference<>();

        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "사용하지 않음",
                (system, input, label) -> {
                    capturedInput.set(input);
                    capturedLabel.set(label);
                    return "대답".repeat(30);
                }
        );

        List<AiMessage> conversation = List.of(
                new AiMessage(
                        1L,
                        AiMessage.Kind.ARGUMENT,
                        "AI는 생산성을 높여 줍니다."
                ),
                new AiMessage(
                        2L,
                        AiMessage.Kind.CHAT,
                        "그 주장에는 동의하기 어렵습니다."
                ),
                new AiMessage(
                        1L,
                        AiMessage.Kind.CHAT,
                        "왜 그렇게 생각하시나요?"
                )
        );

        String result = writer.reply(
                TOPIC,
                Stance.DISAGREE,
                2L,
                conversation
        );

        assertThat(result).hasSize(50);

        assertThat(capturedInput.get())
                .contains("상대방 [최초 주장]")
                .contains("나(AI 봇) [채팅]")
                .contains("왜 그렇게 생각하시나요?");

        assertThat(capturedLabel.get())
                .isEqualTo("bot-chat");
    }

    @Test
    @DisplayName("이모지도 유니코드 코드 포인트 기준으로 제한한다")
    void unicodeLengthLimit() {
        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "😀".repeat(210),
                (system, input, label) -> "사용하지 않음"
        );

        String result = writer.writeArgument(
                TOPIC,
                Stance.AGREE
        );

        assertThat(result.codePointCount(0, result.length()))
                .isEqualTo(200);
    }

    @Test
    @DisplayName("OpenAI 호출 실패 시 예외를 전달한다")
    void apiFailure() {
        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> {
                    throw new AbstractLinerChatClient.LinerChatException(
                            "OPENAI_NETWORK_ERROR",
                            true
                    );
                },
                (system, input, label) -> "사용하지 않음"
        );

        assertThatThrownBy(
                () -> writer.writeArgument(TOPIC, Stance.AGREE)
        )
                .isInstanceOf(
                        AbstractLinerChatClient.LinerChatException.class
                )
                .satisfies(error -> {
                    AbstractLinerChatClient.LinerChatException exception =
                            (AbstractLinerChatClient.LinerChatException) error;

                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("AI가 빈 문자열을 반환하면 예외가 발생한다")
    void emptyResponse() {
        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "   ",
                (system, input, label) -> "사용하지 않음"
        );

        assertThatThrownBy(
                () -> writer.writeArgument(TOPIC, Stance.AGREE)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI 응답이 비어 있습니다.");
    }
}
