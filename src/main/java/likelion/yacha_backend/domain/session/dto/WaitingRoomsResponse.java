
package likelion.yacha_backend.domain.session.dto;

import java.util.List;
import java.util.function.Function;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.domain.user.entity.User;
import org.springframework.data.domain.Slice;

/**
 * GET /sessions/waiting — 대기방 목록 응답.
 */
public record WaitingRoomsResponse(
        List<Room> rooms,
        boolean hasNext
) {

    public static WaitingRoomsResponse from(
            Slice<DebateParticipant> hosts,
            Function<PhilosopherType, String> imageUrlOf
    ) {
        return new WaitingRoomsResponse(
                hosts.map(host -> Room.from(host, imageUrlOf))
                        .getContent(),
                hosts.hasNext()
        );
    }

    /**
     * 대기방 카드 한 개.
     */
    public record Room(
            Long sessionId,
            Host host,
            RoomTopic topic,
            Stance wantedStance
    ) {

        static Room from(
                DebateParticipant host,
                Function<PhilosopherType, String> imageUrlOf
        ) {
            Topic topic = host.getSession().getTopic();
            User user = host.getUser();

            PhilosopherType type = user.getPhilosopherType();

            return new Room(
                    host.getSession().getId(),
                    new Host(
                            user.getNickname(),
                            type,
                            imageUrlOf.apply(type)
                    ),
                    new RoomTopic(
                            topic.getId(),
                            topic.getStatement()
                    ),
                    host.getStance().opposite()
            );
        }
    }

    /**
     * 대기방 방장 정보.
     */
    public record Host(
            String nickname,
            PhilosopherType philosopherType,
            String imageUrl
    ) {
    }

    public record RoomTopic(
            Long id,
            String statement
    ) {
    }
}
