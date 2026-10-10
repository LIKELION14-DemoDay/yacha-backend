package likelion.yacha_backend.domain.session.dto;

import java.time.OffsetDateTime;

/**
 * 채팅 마지막 30초 — 프론트가 상단에 "최종반론" 안내를 띄웁니다 ({@code /topic/sessions/{id}}, 명세 2-4).
 * 채팅은 그대로 이어집니다.
 *
 * @param type   항상 {@code FINAL_NOTICE}
 * @param endsAt 채팅이 끝나는 시각
 */
public record FinalNoticeEvent(String type, OffsetDateTime endsAt) {

    public static FinalNoticeEvent of(OffsetDateTime endsAt) {
        return new FinalNoticeEvent("FINAL_NOTICE", endsAt);
    }
}
