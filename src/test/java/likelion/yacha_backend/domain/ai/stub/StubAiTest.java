package likelion.yacha_backend.domain.ai.stub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import likelion.yacha_backend.domain.ai.AiMessage;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.BotWriter;
import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.ai.JudgeCriterion;
import likelion.yacha_backend.domain.ai.JudgeRequest;
import likelion.yacha_backend.domain.ai.JudgeResult;
import likelion.yacha_backend.domain.session.entity.Stance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("임시 AI 구현 — 글자 수 · 점수 범위")
class StubAiTest {

    /** 입장 문구가 컬럼 상한(100자)까지 차도 글자 수를 넘지 않아야 합니다. */
    private static final AiTopic LONG_TOPIC =
            new AiTopic("명제", "가".repeat(100), "나".repeat(100));

    @Nested
    @DisplayName("힌트")
    class Hint {

        @Test
        @DisplayName("3개, 각 20자 이내다")
        void countAndLength() {
            List<String> hints = new StubHintGenerator().generate(LONG_TOPIC, Stance.AGREE);

            assertThat(hints).hasSize(HintGenerator.HINT_COUNT)
                    .allSatisfy(hint -> assertThat(length(hint)).isLessThanOrEqualTo(HintGenerator.HINT_MAX_LENGTH));
        }
    }

    @Nested
    @DisplayName("봇")
    class Bot {

        private final StubBotWriter writer = new StubBotWriter();

        @Test
        @DisplayName("주장은 200자, 반론은 250자 이내이고 봇 입장 문구가 들어간다")
        void argumentAndRebuttal() {
            String argument = writer.writeArgument(LONG_TOPIC, Stance.DISAGREE);
            String rebuttal = writer.writeRebuttal(LONG_TOPIC, Stance.DISAGREE, "상대 주장");

            assertThat(length(argument)).isLessThanOrEqualTo(200);
            assertThat(length(rebuttal)).isLessThanOrEqualTo(250);
            assertThat(argument).contains(LONG_TOPIC.disagreeText());
            assertThat(rebuttal).contains(LONG_TOPIC.disagreeText());
        }

        @Test
        @DisplayName("채팅 대답은 50자 이내이고, 봇이 대답한 수에 따라 다음 문장으로 넘어간다")
        void reply() {
            Long botId = 2L;
            List<AiMessage> conversation = new ArrayList<>();
            conversation.add(new AiMessage(1L, AiMessage.Kind.CHAT, "안녕"));

            String first = writer.reply(LONG_TOPIC, Stance.DISAGREE, botId, conversation);
            conversation.add(new AiMessage(botId, AiMessage.Kind.CHAT, first));
            conversation.add(new AiMessage(1L, AiMessage.Kind.CHAT, "그래서?"));
            String second = writer.reply(LONG_TOPIC, Stance.DISAGREE, botId, conversation);

            assertThat(first).isNotEqualTo(second);
            assertThat(StubBotWriter.REPLIES)
                    .allSatisfy(reply -> assertThat(length(reply)).isLessThanOrEqualTo(BotWriter.CHAT_MAX_LENGTH));
        }
    }

    @Nested
    @DisplayName("판정")
    class Judging {

        @Test
        @DisplayName("두 참가자 모두 기준마다 10 ~ 25점이고, 요약이 4줄이다")
        void scores() {
            StubJudge judge = new StubJudge(new SplittableRandom(1));
            JudgeRequest request = new JudgeRequest(LONG_TOPIC, List.of(
                    new JudgeRequest.Participant(7L, Stance.AGREE),
                    new JudgeRequest.Participant(8L, Stance.DISAGREE)), List.of());

            for (int i = 0; i < 100; i++) {
                JudgeResult result = judge.judge(request);

                assertThat(result.summaries()).hasSize(JudgeCriterion.values().length);
                for (Long participantId : List.of(7L, 8L)) {
                    assertThat(result.scoreOf(participantId).scores().values())
                            .allSatisfy(score -> assertThat(score).isBetween(StubJudge.MIN_SCORE, JudgeCriterion.MAX_SCORE));
                }
            }
        }
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
