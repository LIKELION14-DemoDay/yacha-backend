package likelion.yacha_backend.domain.session.dto;

import java.util.List;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.topic.entity.Topic;
import org.springframework.data.domain.Slice;

/**
 * {@code GET /sessions/waiting} — 방 찾기 목록 (명세 2-3-1). 오래된 방부터입니다.
 *
 * @param hasNext 다음 페이지가 있는가
 */
public record WaitingRoomsResponse(List<Room> rooms, boolean hasNext) {

    /** 방장 참가자들(세션 · 주제 · 계정을 함께 읽은 것)로 만듭니다. */
    public static WaitingRoomsResponse from(Slice<DebateParticipant> hosts) {
        return new WaitingRoomsResponse(hosts.map(Room::from).getContent(), hosts.hasNext());
    }

    /**
     * 카드 한 장.
     *
     * @param wantedStance 찾는 입장 (방장의 반대). 들어가면 이 입장을 맡습니다
     */
    public record Room(Long sessionId, Host host, RoomTopic topic, Stance wantedStance) {

        static Room from(DebateParticipant host) {
            Topic topic = host.getSession().getTopic();
            return new Room(host.getSession().getId(), new Host(host.getUser().getNickname(), null),
                    new RoomTopic(topic.getId(), topic.getStatement()), host.getStance().opposite());
        }
    }

    /** @param philosopherType 철학자 유형. 아직 users 에 없어 null (결과 화면과 같음) */
    public record Host(String nickname, String philosopherType) {
    }

    public record RoomTopic(Long id, String statement) {
    }
}
