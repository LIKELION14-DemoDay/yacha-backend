
package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import likelion.yacha_backend.domain.ai.AiException;
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
    @DisplayName("AI 채팅은 기존 대화를 참고하고 100자 이내 대답은 그대로 돌려준다")
    void reply() {
        AtomicReference<String> capturedInput = new AtomicReference<>();
        AtomicReference<String> capturedLabel = new AtomicReference<>();

        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "사용하지 않음",
                (system, input, label) -> {
                    capturedInput.set(input);
                    capturedLabel.set(label);
                    return "대답".repeat(50);
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

        assertThat(result).isEqualTo("대답".repeat(50));

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
    @DisplayName("Liner 호출 실패 시 예외를 전달한다")
    void apiFailure() {
        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> {
                    throw new AbstractLinerChatClient.LinerChatException(
                            "LINER_NETWORK_ERROR",
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


    @Test
    @DisplayName("최대 글자 수를 넘으면 완성된 문장에서 자른다")
    void truncateAtSentenceBoundary() {
        String completeSentence =
                "가".repeat(140) + "입니다.";

        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) ->
                        completeSentence + " " + "나".repeat(120),
                (system, input, label) -> "사용하지 않음"
        );

        String result = writer.writeArgument(
                TOPIC,
                Stance.AGREE
        );

        assertThat(result).isEqualTo(completeSentence);

        assertThat(
                result.codePointCount(0, result.length())
        ).isLessThanOrEqualTo(200);
    }

    @Test
    @DisplayName("완성된 문장이 없으면 단어 경계에서 자른다")
    void truncateAtWordBoundary() {
        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "논점 ".repeat(100),
                (system, input, label) -> "사용하지 않음"
        );

        String result = writer.writeArgument(
                TOPIC,
                Stance.AGREE
        );

        assertThat(result).endsWith("…");

        assertThat(
                result.codePointCount(0, result.length())
        ).isLessThanOrEqualTo(200);
    }

    @Test
    @DisplayName("AI 채팅이 100자를 넘으면 자르지 않고 더 짧게 다시 요청한다")
    void replyTooLongRequestsAgain() {
        List<String> systems = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String shortReply = "그 말도 일리가 있지만, 일자리를 잃는 사람의 삶은 누가 책임지나요?";

        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "사용하지 않음",
                (system, input, label) -> {
                    systems.add(system);
                    labels.add(label);
                    return labels.size() == 1 ? "가".repeat(120) : shortReply;
                }
        );

        String result = writer.reply(TOPIC, Stance.DISAGREE, 2L, List.of());

        assertThat(result).isEqualTo(shortReply);
        assertThat(labels).containsExactly("bot-chat", "bot-chat-retry");
        assertThat(systems.get(1))
                .contains("방금 작성한 대답이 120자로 100자를 넘었습니다.");
    }

    @Test
    @DisplayName("다시 요청해도 100자를 넘으면 더 요청하지 않고 다시 시도할 수 없는 예외를 던진다 (요청 최대 2번)")
    void replyStillTooLongThrows() {
        List<String> labels = new ArrayList<>();

        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "사용하지 않음",
                (system, input, label) -> {
                    labels.add(label);
                    return "😀".repeat(101);
                }
        );

        assertThatThrownBy(
                () -> writer.reply(TOPIC, Stance.DISAGREE, 2L, List.of())
        )
                .isInstanceOf(AiException.class)
                .satisfies(error ->
                        assertThat(((AiException) error).isRetryable()).isFalse()
                );

        assertThat(labels).containsExactly("bot-chat", "bot-chat-retry");
    }

    @Test
    @DisplayName("이모지도 한 글자로 세어 100자면 그대로 돌려준다")
    void replyEmojiExactlyLimit() {
        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "사용하지 않음",
                (system, input, label) -> "😀".repeat(100)
        );

        String result = writer.reply(TOPIC, Stance.DISAGREE, 2L, List.of());

        assertThat(result.codePointCount(0, result.length())).isEqualTo(100);
    }

}
