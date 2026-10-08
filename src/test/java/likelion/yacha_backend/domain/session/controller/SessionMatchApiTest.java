package likelion.yacha_backend.domain.session.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.ParticipantRole;
import likelion.yacha_backend.domain.session.entity.ParticipantType;
import likelion.yacha_backend.domain.session.entity.SessionMode;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Topic;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SessionFixture.class)
@DisplayName("방 생성 · 입장 · 취소 · 봇전 · 참여 중인 세션 API")
class SessionMatchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private DebateParticipantRepository participantRepository;

    @Autowired
    private GameRegistry gameRegistry;

    /** 입장으로 만들어진 게임. 테스트가 끝나면 메모리에서 지웁니다. */
    private final List<Long> joinedSessionIds = new ArrayList<>();
    private final List<Room> rooms = new ArrayList<>();

    @AfterEach
    void removeGames() {
        joinedSessionIds.forEach(gameRegistry::remove);
        rooms.forEach(fixture::removeGame);
    }

    private ResultActions createAs(Long userId, Role role, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions createAs(Long userId, Long topicId) throws Exception {
        return createAs(userId, Role.USER,
                "{\"roomType\":\"RANDOM\",\"topicId\":%d,\"stance\":\"AGREE\"}".formatted(topicId));
    }

    private ResultActions joinAs(Long userId, Long sessionId) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/join")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, Role.USER)));
        joinedSessionIds.add(sessionId);
        return result;
    }

    private ResultActions cancelAs(Long userId, Long sessionId) throws Exception {
        return mockMvc.perform(delete("/api/v1/sessions/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, Role.USER)));
    }

    private ResultActions startBotAs(Long userId, Role role, String body) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/sessions/bot")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
        String response = result.andReturn().getResponse().getContentAsString();
        if (response.contains("\"sessionId\"")) {
            joinedSessionIds.add(((Number) JsonPath.read(response, "$.data.sessionId")).longValue());
        }
        return result;
    }

    private ResultActions startBotAs(Long userId, Long topicId) throws Exception {
        return startBotAs(userId, Role.USER, "{\"topicId\":%d,\"stance\":\"AGREE\"}".formatted(topicId));
    }

    private ResultActions convertAs(Long userId, Long sessionId) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/ai")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, Role.USER)));
        joinedSessionIds.add(sessionId);
        return result;
    }

    private DebateParticipant aiOf(Long sessionId) {
        return participantRepository.findAllBySession_Id(sessionId).stream()
                .filter(p -> p.getParticipantType() == ParticipantType.AI)
                .findFirst().orElseThrow();
    }

    private ResultActions currentAs(Long userId, Role role) throws Exception {
        return mockMvc.perform(get("/api/v1/sessions/current")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, role)));
    }

    private String bearer(Long userId, Role role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId, role);
    }

    private Room track(Room room) {
        rooms.add(room);
        return room;
    }

    private DebateSession session(Long sessionId) {
        return sessionRepository.findById(sessionId).orElseThrow();
    }

    @Nested
    @DisplayName("POST /sessions — 방 생성")
    class Create {

        @Test
        @DisplayName("랜덤 방이 WAITING 으로 만들어지고, 카테고리는 주제의 카테고리 · 방장 입장은 고른 입장이다")
        void createsWaitingRoom() throws Exception {
            Long userId = fixture.newUser();
            Topic topic = fixture.topic();

            String body = createAs(userId, topic.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").isNumber())
                    .andReturn().getResponse().getContentAsString();

            Long sessionId = ((Number) JsonPath.read(body, "$.data.sessionId")).longValue();
            DebateSession session = session(sessionId);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.WAITING);
            assertThat(session.getCategory()).isEqualTo(Category.ETHICS);
            assertThat(session.getTopic().getId()).isEqualTo(topic.getId());
            DebateParticipant host = participantRepository.findBySession_IdAndUser_Id(sessionId, userId).orElseThrow();
            assertThat(host.getRole()).isEqualTo(ParticipantRole.INITIATOR);
            assertThat(host.getStance()).isEqualTo(Stance.AGREE);
        }

        @Test
        @DisplayName("게스트도 만들 수 있다")
        void guestCanCreate() throws Exception {
            createAs(fixture.newUser(), Role.GUEST,
                    "{\"roomType\":\"RANDOM\",\"topicId\":%d,\"stance\":\"DISAGREE\"}".formatted(fixture.topic().getId()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("이미 대기 중인 방이 있으면 ALREADY_IN_SESSION")
        void alreadyWaiting() throws Exception {
            Room room = fixture.waitingRandom();

            createAs(room.hostUserId(), fixture.topic().getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ALREADY_IN_SESSION"));
        }

        @Test
        @DisplayName("게임 중이면 ALREADY_IN_SESSION")
        void alreadyPlaying() throws Exception {
            Room room = track(fixture.randomHuman(10));

            createAs(room.opponentUserId(), fixture.topic().getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ALREADY_IN_SESSION"));
        }

        @Test
        @DisplayName("없거나 내린 주제면 TOPIC_NOT_FOUND")
        void topicNotFound() throws Exception {
            Long userId = fixture.newUser();
            Topic inactive = fixture.topic();
            inactive.deactivate();

            createAs(userId, inactive.getId())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("TOPIC_NOT_FOUND"));
            createAs(userId, 999_999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("TOPIC_NOT_FOUND"));
        }

        @Test
        @DisplayName("친구 방은 보류라 400, 빠진 값은 VALIDATION_FAILED")
        void invalidRequest() throws Exception {
            Long userId = fixture.newUser();

            createAs(userId, Role.USER, "{\"roomType\":\"FRIEND\",\"topicId\":1,\"stance\":\"AGREE\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
            createAs(userId, Role.USER, "{\"roomType\":\"RANDOM\",\"stance\":\"AGREE\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.data.topicId").exists());
        }
    }

    @Nested
    @DisplayName("POST /sessions/{id}/join — 입장")
    class Join {

        @Test
        @DisplayName("방장의 반대 입장으로 들어가 바로 시작하고, 두 사람이 참가자인 게임이 만들어진다")
        void joinsAndStarts() throws Exception {
            Room room = fixture.waitingRandom();
            Long userId = fixture.newUser();

            joinAs(userId, room.sessionId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(room.sessionId()));

            DebateSession session = session(room.sessionId());
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertThat(session.getStartedAt()).isNotNull();
            DebateParticipant opponent = participantRepository.findBySession_IdAndUser_Id(room.sessionId(), userId)
                    .orElseThrow();
            assertThat(opponent.getRole()).isEqualTo(ParticipantRole.OPPONENT);
            assertThat(opponent.getStance()).isEqualTo(Stance.DISAGREE);
            Game game = gameRegistry.find(room.sessionId()).orElseThrow();
            assertThat(game.getStartedAt()).isEqualTo(session.getStartedAt());
            assertThat(game.participantIdOf(room.hostUserId())).isEqualTo(room.hostParticipantId());
            assertThat(game.participantIdOf(userId)).isEqualTo(opponent.getId());
        }

        @Test
        @DisplayName("내 방이면 CANNOT_JOIN_OWN_ROOM")
        void ownRoom() throws Exception {
            Room room = fixture.waitingRandom();

            joinAs(room.hostUserId(), room.sessionId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CANNOT_JOIN_OWN_ROOM"));
        }

        @Test
        @DisplayName("내 방에서 기다리는 중이면 다른 방에 들어갈 수 없다 (ALREADY_IN_SESSION)")
        void alreadyWaiting() throws Exception {
            Room mine = fixture.waitingRandom();
            Room other = fixture.waitingRandom();

            joinAs(mine.hostUserId(), other.sessionId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ALREADY_IN_SESSION"));
            assertThat(session(other.sessionId()).getStatus()).isEqualTo(SessionStatus.WAITING);
        }

        @Test
        @DisplayName("이미 시작한 방 · 봇전 · 취소된 방이면 SESSION_NOT_WAITING")
        void notWaiting() throws Exception {
            Room started = track(fixture.randomHuman(10));
            Room bot = track(fixture.autoBot(10));
            Room cancelled = fixture.waitingRandom();
            cancelAs(cancelled.hostUserId(), cancelled.sessionId()).andExpect(status().isOk());

            for (Room room : List.of(started, bot, cancelled)) {
                mockMvc.perform(post("/api/v1/sessions/" + room.sessionId() + "/join")
                                .header(HttpHeaders.AUTHORIZATION, bearer(fixture.newUser(), Role.USER)))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.error.code").value("SESSION_NOT_WAITING"));
            }
        }

        @Test
        @DisplayName("없는 방이면 SESSION_NOT_FOUND")
        void notFound() throws Exception {
            joinAs(fixture.newUser(), 999_999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("DELETE /sessions/{id} — 대기 취소")
    class Cancel {

        @Test
        @DisplayName("방장이 취소하면 CANCELLED 가 되고, 바로 새 방을 만들 수 있다")
        void hostCancels() throws Exception {
            Room room = fixture.waitingRandom();

            cancelAs(room.hostUserId(), room.sessionId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(room.sessionId()));

            DebateSession session = session(room.sessionId());
            assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
            assertThat(session.getEndedAt()).isNotNull();
            createAs(room.hostUserId(), fixture.topic().getId()).andExpect(status().isOk());
        }

        @Test
        @DisplayName("방장이 아니면 NOT_ROOM_OWNER")
        void notOwner() throws Exception {
            Room room = fixture.waitingRandom();

            cancelAs(fixture.newUser(), room.sessionId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("NOT_ROOM_OWNER"));
            assertThat(session(room.sessionId()).getStatus()).isEqualTo(SessionStatus.WAITING);
        }

        @Test
        @DisplayName("이미 상대가 들어온 방이면 SESSION_NOT_WAITING")
        void alreadyMatched() throws Exception {
            Room room = fixture.waitingRandom();
            joinAs(fixture.newUser(), room.sessionId()).andExpect(status().isOk());

            cancelAs(room.hostUserId(), room.sessionId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_WAITING"));
        }

        @Test
        @DisplayName("없는 방이면 SESSION_NOT_FOUND")
        void notFound() throws Exception {
            cancelAs(fixture.newUser(), 999_999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /sessions/current — 내가 참여 중인 세션")
    class Current {

        @Test
        @DisplayName("방을 만든 뒤 sessionId 를 잃어도 되찾아 취소하고, 새 방을 만들 수 있다")
        void recoversWaitingRoom() throws Exception {
            Long userId = fixture.newUser();
            Long topicId = fixture.topic().getId();
            String body = createAs(userId, topicId).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Long sessionId = ((Number) JsonPath.read(body, "$.data.sessionId")).longValue();
            createAs(userId, topicId).andExpect(jsonPath("$.error.code").value("ALREADY_IN_SESSION"));

            currentAs(userId, Role.USER)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                    .andExpect(jsonPath("$.data.status").value("WAITING"));

            cancelAs(userId, sessionId).andExpect(status().isOk());
            currentAs(userId, Role.USER)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist());
            createAs(userId, topicId).andExpect(status().isOk());
        }

        @Test
        @DisplayName("게임 중이면 방장 · 입장한 사람 모두 IN_PROGRESS 세션을 받는다")
        void inProgress() throws Exception {
            Room room = track(fixture.randomHuman(10));

            for (Long userId : List.of(room.hostUserId(), room.opponentUserId())) {
                currentAs(userId, Role.USER)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.sessionId").value(room.sessionId()))
                        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
            }
        }

        @Test
        @DisplayName("게스트도 조회할 수 있다")
        void guest() throws Exception {
            Room room = fixture.waitingRandom();

            currentAs(room.hostUserId(), Role.GUEST)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(room.sessionId()));
        }

        @Test
        @DisplayName("참여한 적이 없거나 게임이 끝났으면 data 가 null")
        void none() throws Exception {
            Room finished = track(fixture.randomHuman(10));
            fixture.finish(finished);

            for (Long userId : List.of(fixture.newUser(), finished.hostUserId())) {
                currentAs(userId, Role.USER)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist());
            }
        }

        @Test
        @DisplayName("토큰이 없으면 401")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/sessions/current"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /sessions/bot — 바로 봇전")
    class StartBot {

        @Test
        @DisplayName("봇전이 바로 IN_PROGRESS 로 만들어지고, 봇은 반대 입장이며 게임에 AI 참가자가 들어간다")
        void startsBotMatch() throws Exception {
            Long userId = fixture.newUser();
            Topic topic = fixture.topic();

            String body = startBotAs(userId, topic.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").isNumber())
                    .andReturn().getResponse().getContentAsString();

            Long sessionId = ((Number) JsonPath.read(body, "$.data.sessionId")).longValue();
            DebateSession session = session(sessionId);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertThat(session.getMode()).isEqualTo(SessionMode.AI);
            assertThat(session.getCategory()).isEqualTo(Category.ETHICS);
            assertThat(session.getTopic().getId()).isEqualTo(topic.getId());
            DebateParticipant me = participantRepository.findBySession_IdAndUser_Id(sessionId, userId).orElseThrow();
            assertThat(me.getRole()).isEqualTo(ParticipantRole.INITIATOR);
            assertThat(me.getStance()).isEqualTo(Stance.AGREE);
            DebateParticipant ai = aiOf(sessionId);
            assertThat(ai.getRole()).isEqualTo(ParticipantRole.OPPONENT);
            assertThat(ai.getStance()).isEqualTo(Stance.DISAGREE);
            Game game = gameRegistry.find(sessionId).orElseThrow();
            assertThat(game.getStartedAt()).isEqualTo(session.getStartedAt());
            assertThat(game.participantIdOf(userId)).isEqualTo(me.getId());
            assertThat(game.getAiParticipantId()).isEqualTo(ai.getId());
        }

        @Test
        @DisplayName("게스트도 시작할 수 있고, /current 로 봇전을 되찾는다")
        void guestAndCurrent() throws Exception {
            Long userId = fixture.newUser();
            String body = startBotAs(userId, Role.GUEST,
                    "{\"topicId\":%d,\"stance\":\"DISAGREE\"}".formatted(fixture.topic().getId()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Long sessionId = ((Number) JsonPath.read(body, "$.data.sessionId")).longValue();

            currentAs(userId, Role.GUEST)
                    .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                    .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        }

        @Test
        @DisplayName("이미 대기 · 진행 중인 세션이 있으면 ALREADY_IN_SESSION")
        void alreadyInSession() throws Exception {
            Room waiting = fixture.waitingRandom();
            Room playing = track(fixture.randomHuman(10));

            for (Long userId : List.of(waiting.hostUserId(), playing.opponentUserId())) {
                startBotAs(userId, fixture.topic().getId())
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.error.code").value("ALREADY_IN_SESSION"));
            }
        }

        @Test
        @DisplayName("없거나 내린 주제면 TOPIC_NOT_FOUND, 빠진 값은 VALIDATION_FAILED")
        void invalidRequest() throws Exception {
            Long userId = fixture.newUser();
            Topic inactive = fixture.topic();
            inactive.deactivate();

            startBotAs(userId, inactive.getId())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("TOPIC_NOT_FOUND"));
            startBotAs(userId, Role.USER, "{\"topicId\":1}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("POST /sessions/{id}/ai — 대기 중 AI 전환")
    class ConvertToAi {

        @Test
        @DisplayName("방장이 전환하면 AI 모드로 바로 시작하고, 봇은 방장의 반대 입장이다")
        void hostConverts() throws Exception {
            Room room = fixture.waitingRandom();

            convertAs(room.hostUserId(), room.sessionId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(room.sessionId()));

            DebateSession session = session(room.sessionId());
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertThat(session.getMode()).isEqualTo(SessionMode.AI);
            DebateParticipant ai = aiOf(room.sessionId());
            assertThat(ai.getStance()).isEqualTo(Stance.DISAGREE);
            Game game = gameRegistry.find(room.sessionId()).orElseThrow();
            assertThat(game.getStartedAt()).isEqualTo(session.getStartedAt());
            assertThat(game.participantIdOf(room.hostUserId())).isEqualTo(room.hostParticipantId());
            assertThat(game.getAiParticipantId()).isEqualTo(ai.getId());
        }

        @Test
        @DisplayName("전환된 방에는 들어갈 수 없고 취소 · 다시 전환도 안 된다 (SESSION_NOT_WAITING)")
        void afterConversion() throws Exception {
            Room room = fixture.waitingRandom();
            convertAs(room.hostUserId(), room.sessionId()).andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/sessions/" + room.sessionId() + "/join")
                            .header(HttpHeaders.AUTHORIZATION, bearer(fixture.newUser(), Role.USER)))
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_WAITING"));
            cancelAs(room.hostUserId(), room.sessionId())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_WAITING"));
            convertAs(room.hostUserId(), room.sessionId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_WAITING"));
        }

        @Test
        @DisplayName("방장이 아니면 NOT_ROOM_OWNER")
        void notOwner() throws Exception {
            Room room = fixture.waitingRandom();

            convertAs(fixture.newUser(), room.sessionId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("NOT_ROOM_OWNER"));
            assertThat(session(room.sessionId()).getStatus()).isEqualTo(SessionStatus.WAITING);
        }

        @Test
        @DisplayName("이미 상대가 들어왔거나 취소된 방이면 SESSION_NOT_WAITING")
        void notWaiting() throws Exception {
            Room matched = fixture.waitingRandom();
            joinAs(fixture.newUser(), matched.sessionId()).andExpect(status().isOk());
            Room cancelled = fixture.waitingRandom();
            cancelAs(cancelled.hostUserId(), cancelled.sessionId()).andExpect(status().isOk());

            for (Room room : List.of(matched, cancelled)) {
                convertAs(room.hostUserId(), room.sessionId())
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.error.code").value("SESSION_NOT_WAITING"));
            }
            assertThat(participantRepository.findAllBySession_Id(matched.sessionId()))
                    .noneMatch(p -> p.getParticipantType() == ParticipantType.AI);
        }

        @Test
        @DisplayName("없는 방이면 SESSION_NOT_FOUND")
        void notFound() throws Exception {
            convertAs(fixture.newUser(), 999_999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        }
    }
}
