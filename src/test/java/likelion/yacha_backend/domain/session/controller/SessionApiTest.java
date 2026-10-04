package likelion.yacha_backend.domain.session.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spectate.enabled=true")
@AutoConfigureMockMvc
@Transactional
@Import(SessionFixture.class)
@DisplayName("토론방 조회 — 현재 상태 · 메시지")
class SessionApiTest {

    /** 시작 후 90초 = CHAT_1 */
    private static final long IN_CHAT_1 = 90;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private DebateParticipantRepository participantRepository;

    private Room room;

    @AfterEach
    void removeGame() {
        fixture.removeGame(room);
    }

    private ResultActions getAs(Long userId, String path) throws Exception {
        return mockMvc.perform(get("/api/v1/sessions/" + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(userId, Role.USER)));
    }

    @Nested
    @DisplayName("GET /sessions/{id}/state")
    class State {

        @Test
        @DisplayName("참가자는 현재 구간 · 끝나는 시각 · 서버 시각(+09:00) · 내 참가자 id 를 받는다")
        void participant() throws Exception {
            room = fixture.randomHuman(IN_CHAT_1);

            getAs(room.hostUserId(), room.sessionId() + "/state")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(room.sessionId()))
                    .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.data.roomType").value("RANDOM"))
                    .andExpect(jsonPath("$.data.mode").value("HUMAN"))
                    .andExpect(jsonPath("$.data.phase").value("CHAT_1"))
                    .andExpect(jsonPath("$.data.endsAt", endsWith("+09:00")))
                    .andExpect(jsonPath("$.data.serverNow", endsWith("+09:00")))
                    .andExpect(jsonPath("$.data.myParticipantId").value(room.hostParticipantId()))
                    .andExpect(jsonPath("$.data.participants.length()").value(2));
        }

        @Test
        @DisplayName("승패와 사용자 id 는 응답에 없다")
        void hidesResultAndUserId() throws Exception {
            room = fixture.randomHuman(IN_CHAT_1);

            getAs(room.hostUserId(), room.sessionId() + "/state")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.participants[0].result").doesNotExist())
                    .andExpect(jsonPath("$.data.participants[0].userId").doesNotExist())
                    .andExpect(jsonPath("$.data.participants[0].participantId").isNumber());
        }

        @Test
        @DisplayName("진행 중인 랜덤 사람전은 관전자도 조회하고, 내 참가자 id 는 null")
        void spectatorOfRandomHuman() throws Exception {
            room = fixture.randomHuman(IN_CHAT_1);

            getAs(fixture.newUser(), room.sessionId() + "/state")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.phase").value("CHAT_1"))
                    .andExpect(jsonPath("$.data.myParticipantId").doesNotExist());
        }

        @Test
        @DisplayName("친구 방은 관전자가 조회할 수 없다 (NOT_PARTICIPANT)")
        void spectatorOfFriendRoom() throws Exception {
            room = fixture.friend(IN_CHAT_1);

            getAs(fixture.newUser(), room.sessionId() + "/state")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
        }

        @Test
        @DisplayName("대기 중 AI 로 바뀐 봇전은 관전자가 조회할 수 없다 (RANDOM 방이어도)")
        void spectatorOfConvertedBot() throws Exception {
            room = fixture.convertedBot(IN_CHAT_1);

            getAs(fixture.newUser(), room.sessionId() + "/state")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
        }

        @Test
        @DisplayName("자동 봇전은 관전자가 조회할 수 없다 (RANDOM 방이어도)")
        void spectatorOfAutoBot() throws Exception {
            room = fixture.autoBot(IN_CHAT_1);

            getAs(fixture.newUser(), room.sessionId() + "/state")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
        }

        @Test
        @DisplayName("상대 게스트 계정이 정리돼 참가 기록의 user 가 비어도 200 (내 참가자 id 도 정상)")
        void detachedOpponent() throws Exception {
            room = fixture.randomHuman(IN_CHAT_1);
            // 방장(앞쪽 참가자)이 정리된 게스트라 연결이 끊긴 상황
            participantRepository.detachUsers(List.of(room.hostUserId()));

            getAs(room.opponentUserId(), room.sessionId() + "/state")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.myParticipantId").value(room.opponentParticipantId()))
                    .andExpect(jsonPath("$.data.participants.length()").value(2));
        }

        @Test
        @DisplayName("봇전 참가자는 자기 방을 조회하고 참가자 목록에 AI 가 있다")
        void participantOfBot() throws Exception {
            room = fixture.autoBot(IN_CHAT_1);

            getAs(room.hostUserId(), room.sessionId() + "/state")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.mode").value("AI"))
                    .andExpect(jsonPath("$.data.participants[?(@.type == 'AI')]").exists());
        }

        @Test
        @DisplayName("대기 중인 방은 구간이 없고, 관전자는 조회할 수 없다")
        void waitingRoom() throws Exception {
            room = fixture.waitingRandom();

            getAs(room.hostUserId(), room.sessionId() + "/state")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("WAITING"))
                    .andExpect(jsonPath("$.data.phase").doesNotExist())
                    .andExpect(jsonPath("$.data.endsAt").doesNotExist());
            getAs(fixture.newUser(), room.sessionId() + "/state")
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("없는 세션은 SESSION_NOT_FOUND")
        void notFound() throws Exception {
            getAs(fixture.newUser(), "999999/state")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        }

        @Test
        @DisplayName("토큰 없이 호출하면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/v1/sessions/1/state"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /sessions/{id}/messages")
    class Messages {

        @Test
        @DisplayName("afterSeq 보다 뒤의 메시지를 오름차순으로 받는다")
        void afterSeq() throws Exception {
            room = fixture.randomHuman(IN_CHAT_1);
            Game game = gameRegistry.find(room.sessionId()).orElseThrow();
            LocalDateTime now = LocalDateTime.now();
            game.appendChat(room.hostUserId(), "첫째", now);
            game.appendChat(room.opponentUserId(), "둘째", now);
            game.appendChat(room.hostUserId(), "셋째", now);

            getAs(room.opponentUserId(), room.sessionId() + "/messages?afterSeq=1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].seqNo").value(2))
                    .andExpect(jsonPath("$.data[0].type").value("CHAT"))
                    .andExpect(jsonPath("$.data[0].senderId").value(room.opponentParticipantId()))
                    .andExpect(jsonPath("$.data[0].content").value("둘째"))
                    .andExpect(jsonPath("$.data[0].receivedAt", endsWith("+09:00")))
                    .andExpect(jsonPath("$.data[1].seqNo").value(3));
        }

        @Test
        @DisplayName("늦게 들어온 관전자는 afterSeq 없이 처음부터 받는다")
        void spectatorFromStart() throws Exception {
            room = fixture.randomHuman(IN_CHAT_1);
            gameRegistry.find(room.sessionId()).orElseThrow()
                    .appendChat(room.hostUserId(), "주장", LocalDateTime.now());

            getAs(fixture.newUser(), room.sessionId() + "/messages")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].seqNo").value(1));
        }

        @Test
        @DisplayName("친구 방 · 봇전의 메시지는 관전자가 볼 수 없다")
        void privateRooms() throws Exception {
            Room friend = fixture.friend(IN_CHAT_1);
            Room bot = fixture.convertedBot(IN_CHAT_1);
            try {
                getAs(fixture.newUser(), friend.sessionId() + "/messages")
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
                getAs(fixture.newUser(), bot.sessionId() + "/messages")
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
            } finally {
                fixture.removeGame(friend);
                fixture.removeGame(bot);
            }
        }

        @Test
        @DisplayName("끝난 토론은 메시지가 메모리에 남아 있어도 참가자가 조회할 수 없다 (SESSION_NOT_IN_PROGRESS)")
        void finished() throws Exception {
            room = fixture.randomHuman(IN_CHAT_1);
            gameRegistry.find(room.sessionId()).orElseThrow()
                    .appendChat(room.hostUserId(), "주장", LocalDateTime.now());
            fixture.finish(room);

            getAs(room.hostUserId(), room.sessionId() + "/messages")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_IN_PROGRESS"));
        }

        @Test
        @DisplayName("게임이 메모리에 없으면(시작 전 · 정리됨) SESSION_NOT_IN_PROGRESS")
        void noGame() throws Exception {
            room = fixture.waitingRandom();

            getAs(room.hostUserId(), room.sessionId() + "/messages")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_IN_PROGRESS"));
        }
    }
}
