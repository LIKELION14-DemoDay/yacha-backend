
package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import likelion.yacha_backend.domain.ai.AiMessage;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.session.entity.Stance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Liner 봇 채팅 첫마디/답변 프롬프트 테스트")
class LinerBotFirstMessageTest {

    private static final AiTopic TOPIC = new AiTopic(
            "AI가 인간의 일자리를 대체하는 것은 바람직한가?",
            "AI가 인간의 일자리를 대체하는 것은 바람직하다",
            "AI가 인간의 일자리를 대체하는 것은 바람직하지 않다"
    );

    @Test
    @DisplayName("사람이 채팅하지 않았으면 먼저 말을 거는 프롬프트를 사용한다")
    void firstMessageWhenHumanSilent() {
        AtomicReference<String> prompt = new AtomicReference<>();

        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "사용하지 않음",
                (system, input, label) -> {
                    prompt.set(system);
                    return "그 이유에 대해 어떻게 생각하시나요?";
                }
        );

        String result = writer.reply(
                TOPIC,
                Stance.AGREE,
                1L,
                List.of(
                        new AiMessage(
                                1L,
                                AiMessage.Kind.ARGUMENT,
                                "찬성 주장"
                        ),
                        new AiMessage(
                                2L,
                                AiMessage.Kind.REBUTTAL,
                                "반대 반론"
                        )
                )
        );

        assertThat(result).isNotBlank();

        assertThat(prompt.get())
                .contains("먼저 자연스럽게 말을 거세요")
                .doesNotContain("최근 채팅 발언에 직접 응답하세요");
    }

    @Test
    @DisplayName("사람의 채팅이 있으면 그 발언에 답하는 프롬프트를 사용한다")
    void replyWhenHumanChatExists() {
        AtomicReference<String> prompt = new AtomicReference<>();

        LinerBotWriter writer = new LinerBotWriter(
                (system, input, label) -> "사용하지 않음",
                (system, input, label) -> {
                    prompt.set(system);
                    return "그 주장에 대한 근거가 있습니다.";
                }
        );

        String result = writer.reply(
                TOPIC,
                Stance.AGREE,
                1L,
                List.of(
                        new AiMessage(
                                1L,
                                AiMessage.Kind.ARGUMENT,
                                "찬성 주장"
                        ),
                        new AiMessage(
                                2L,
                                AiMessage.Kind.CHAT,
                                "반대합니다. 이유가 있나요?"
                        )
                )
        );

        assertThat(result).isNotBlank();

        assertThat(prompt.get())
                .contains("최근 채팅 발언에 직접 응답하세요")
                .doesNotContain("먼저 자연스럽게 말을 거세요");
    }
}
