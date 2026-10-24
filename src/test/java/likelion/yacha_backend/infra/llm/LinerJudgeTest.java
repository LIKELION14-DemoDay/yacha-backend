
package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import likelion.yacha_backend.domain.ai.AiMessage;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.JudgeCriterion;
import likelion.yacha_backend.domain.ai.JudgeRequest;
import likelion.yacha_backend.domain.ai.JudgeResult;
import likelion.yacha_backend.domain.session.entity.Stance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Liner AI 판정 단위 테스트")
class LinerJudgeTest {

    private static final AiTopic TOPIC = new AiTopic(
            "AI가 인간의 일자리를 대체하는 것은 바람직한가?",
            "AI가 인간의 일자리를 대체하는 것은 바람직하다",
            "AI가 인간의 일자리를 대체하는 것은 바람직하지 않다"
    );

    private static final JudgeRequest REQUEST = new JudgeRequest(
            TOPIC,
            List.of(
                    new JudgeRequest.Participant(
                            101L,
                            Stance.AGREE
                    ),
                    new JudgeRequest.Participant(
                            202L,
                            Stance.DISAGREE
                    )
            ),
            List.of(
                    new AiMessage(
                            101L,
                            AiMessage.Kind.ARGUMENT,
                            "AI는 생산성을 높이고 새로운 직업을 만듭니다."
                    ),
                    new AiMessage(
                            202L,
                            AiMessage.Kind.ARGUMENT,
                            "AI는 실업과 소득 격차를 심화시킬 수 있습니다."
                    ),
                    new AiMessage(
                            101L,
                            AiMessage.Kind.REBUTTAL,
                            "기술 변화에 맞춘 재교육으로 대응할 수 있습니다."
                    ),
                    new AiMessage(
                            202L,
                            AiMessage.Kind.CHAT,
                            "모든 사람이 재교육받을 기회를 얻지는 못합니다."
                    )
            )
    );

    private static final String VALID_RESPONSE = """
            {
              "scores": [
                {
                  "participantId": 101,
                  "scores": {
                    "LOGIC": 20,
                    "EVIDENCE": 19,
                    "REBUTTAL": 17,
                    "CONSISTENCY": 18
                  }
                },
                {
                  "participantId": 202,
                  "scores": {
                    "LOGIC": 16,
                    "EVIDENCE": 15,
                    "REBUTTAL": 14,
                    "CONSISTENCY": 17
                  }
                }
              ],
              "summaries": {
                "LOGIC": "101번 참가자의 논리 전개가 더 명확합니다.",
                "EVIDENCE": "101번 참가자가 구체적인 근거를 제시했습니다.",
                "REBUTTAL": "101번 참가자의 반론이 상대 주장에 더 직접적입니다.",
                "CONSISTENCY": "두 참가자 모두 입장을 유지했습니다."
              }
            }
            """;

    @Test
    @DisplayName("두 참가자를 한 번의 API 호출로 함께 판정한다")
    void judgeTwoParticipantsInOneCall() {

        AtomicInteger callCount = new AtomicInteger();
        AtomicReference<String> capturedPrompt =
                new AtomicReference<>();

        LinerJudge judge = new LinerJudge("", "liner-mark") {

            @Override
            protected String chat(
                    String systemPrompt,
                    String userContent,
                    String logLabel
            ) {
                callCount.incrementAndGet();
                capturedPrompt.set(userContent);

                return VALID_RESPONSE;
            }
        };

        JudgeResult result = judge.judge(REQUEST);

        assertThat(callCount.get()).isEqualTo(1);

        assertThat(result.scores()).hasSize(2);

        assertThat(result.scoreOf(101L).total())
                .isEqualTo(74);

        assertThat(result.scoreOf(202L).total())
                .isEqualTo(62);

        assertThat(result.summaries())
                .hasSize(4);

        assertThat(capturedPrompt.get())
                .contains(TOPIC.statement())
                .contains(TOPIC.agreeText())
                .contains(TOPIC.disagreeText())
                .contains("101")
                .contains("202")
                .contains("생산성을 높이고")
                .contains("실업과 소득 격차")
                .contains("재교육");
    }

    @Test
    @DisplayName("JSON이 Markdown 코드 블록으로 감싸져도 처리한다")
    void markdownCodeFence() {

        String response =
                "```json\n" + VALID_RESPONSE + "\n```";

        LinerJudge judge = fakeJudge(response);

        JudgeResult result = judge.judge(REQUEST);

        assertThat(result.scores()).hasSize(2);

        assertThat(result.scoreOf(101L).total())
                .isEqualTo(74);
    }

    @Test
    @DisplayName("점수가 25점을 초과하면 예외를 발생시킨다")
    void scoreOutOfRange() {

        String invalidResponse = VALID_RESPONSE.replace(
                "\"LOGIC\": 20",
                "\"LOGIC\": 26"
        );

        LinerJudge judge = fakeJudge(invalidResponse);

        assertThatThrownBy(
                () -> judge.judge(REQUEST)
        )
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("점수가 정수가 아니면 예외를 발생시킨다")
    void nonIntegerScore() {

        String invalidResponse = VALID_RESPONSE.replace(
                "\"LOGIC\": 20",
                "\"LOGIC\": 20.5"
        );

        LinerJudge judge = fakeJudge(invalidResponse);

        assertThatThrownBy(
                () -> judge.judge(REQUEST)
        )
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("요청에 없는 참가자 ID가 있으면 거부한다")
    void unknownParticipant() {

        String invalidResponse = VALID_RESPONSE.replace(
                "\"participantId\": 202",
                "\"participantId\": 303"
        );

        LinerJudge judge = fakeJudge(invalidResponse);

        assertThatThrownBy(
                () -> judge.judge(REQUEST)
        )
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("참가자 ID가 중복되면 예외를 발생시킨다")
    void duplicateParticipant() {

        String invalidResponse = VALID_RESPONSE.replace(
                "\"participantId\": 202",
                "\"participantId\": 101"
        );

        LinerJudge judge = fakeJudge(invalidResponse);

        assertThatThrownBy(
                () -> judge.judge(REQUEST)
        )
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("판정 기준이 빠지면 예외를 발생시킨다")
    void missingCriterion() {

        String invalidResponse = VALID_RESPONSE.replace(
                "\"CONSISTENCY\": 18",
                "\"OTHER\": 18"
        );

        LinerJudge judge = fakeJudge(invalidResponse);

        assertThatThrownBy(
                () -> judge.judge(REQUEST)
        )
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("잘못된 JSON이면 예외를 발생시킨다")
    void invalidJson() {

        LinerJudge judge = fakeJudge(
                "{\"scores\":"
        );

        assertThatThrownBy(
                () -> judge.judge(REQUEST)
        )
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("판정 요약 네 개를 모두 반환한다")
    void fourSummaries() {

        LinerJudge judge = fakeJudge(VALID_RESPONSE);

        JudgeResult result = judge.judge(REQUEST);

        for (JudgeCriterion criterion
                : JudgeCriterion.values()) {

            assertThat(result.summaries())
                    .containsKey(criterion);

            assertThat(result.summaries().get(criterion))
                    .isNotBlank();
        }
    }

    /**
     * 실제 Liner 호출 없이 지정한 JSON을 반환하는 테스트용 판정기.
     */
    private LinerJudge fakeJudge(String response) {

        return new LinerJudge("", "liner-mark") {

            @Override
            protected String chat(
                    String systemPrompt,
                    String userContent,
                    String logLabel
            ) {
                return response;
            }
        };
    }
}
