package likelion.yacha_backend.domain.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.topic.dto.TopicResponse;

/**
 * 자동 제안 · 제안 거절 응답 (명세 2-3-1). 값이 없는 필드는 보내지 않습니다.
 *
 * @param result    {@code PROPOSAL} 이 방을 보여줌 / {@code NEW_TOPIC} 한 바퀴를 돌아 새 주제
 * @param sessionId {@code PROPOSAL} 만 — 승낙하면 {@code POST /sessions/{id}/join}, 거절하면 {@code /reject}
 * @param topic     {@code PROPOSAL} 은 그 방의 주제, {@code NEW_TOPIC} 은 고른 카테고리에서 뽑은 새 주제
 * @param myStance  {@code PROPOSAL} 만 — 들어가면 맡을 입장 (방장의 반대)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProposalResponse(Result result, Long sessionId, TopicResponse topic, Stance myStance) {

    public enum Result {
        PROPOSAL,
        NEW_TOPIC,
    }

    public static ProposalResponse proposal(Long sessionId, TopicResponse topic, Stance myStance) {
        return new ProposalResponse(Result.PROPOSAL, sessionId, topic, myStance);
    }

    public static ProposalResponse newTopic(TopicResponse topic) {
        return new ProposalResponse(Result.NEW_TOPIC, null, topic, null);
    }
}
