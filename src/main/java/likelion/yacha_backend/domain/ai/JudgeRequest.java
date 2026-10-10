package likelion.yacha_backend.domain.ai;

import java.util.List;
import java.util.Objects;
import likelion.yacha_backend.domain.session.entity.Stance;

/**
 * 판정 입력 (명세 2-6). 두 사람을 한 번에 채점하도록 대화 전체를 함께 넘깁니다.
 *
 * @param topic        주제
 * @param participants 두 참가자
 * @param messages     공개된 주장 · 반론과 채팅 전체 ({@code seqNo} 순서)
 */
public record JudgeRequest(AiTopic topic, List<Participant> participants, List<AiMessage> messages) {

    public JudgeRequest {
        Objects.requireNonNull(topic, "topic");
        participants = List.copyOf(participants);
        messages = List.copyOf(messages);
        if (participants.size() != 2) {
            throw new IllegalArgumentException("참가자는 두 명이어야 합니다: " + participants.size());
        }
    }

    /**
     * @param participantId 참가자 id. {@link AiMessage#participantId()} 와 같은 값
     * @param stance        입장
     */
    public record Participant(Long participantId, Stance stance) {

        public Participant {
            Objects.requireNonNull(participantId, "participantId");
            Objects.requireNonNull(stance, "stance");
        }
    }
}
