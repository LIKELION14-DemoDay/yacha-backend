package likelion.yacha_backend.domain.session.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.session.repository.ProposalCursorStore;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동 제안 · 제안 거절 · 방 찾기 (명세 2-3-2). 대기열은 DB 전체를 보므로, 테스트마다 먼저 다른 테스트가 남긴
 * 대기 방을 이 트랜잭션 안에서 취소해 두고 시작합니다 (끝나면 롤백).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("자동 제안 · 제안 거절 · 방 찾기 API")
class SessionProposalApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private DebateParticipantRepository participantRepository;

    @Autowired
    private ProposalCursorStore cursorStore;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private EntityManager entityManager;

    private final List<Long> joinedSessionIds = new ArrayList<>();
    private Long me;

    @BeforeEach
    void setUp() {
        entityManager.createQuery("update DebateSession s set s.status = :cancelled where s.status = :waiting")
                .setParameter("cancelled", SessionStatus.CANCELLED)
                .setParameter("waiting", SessionStatus.WAITING)
                .executeUpdate();
        me = newUser();
    }

    @AfterEach
    void tearDown() {
        joinedSessionIds.forEach(gameRegistry::remove);
        cursorStore.delete(me);
    }

    private Long newUser() {
        return userRepository.save(User.createGuest("u" + UUID.randomUUID().toString().substring(0, 8))).getId();
    }

    private Topic topic(Subcategory subcategory) {
        return topicRepository.save(Topic.create(subcategory, "주제 " + subcategory, "그렇다", "아니다"));
    }

    /** 대기 중인 랜덤 방. 방장은 새 사용자이고, 먼저 만든 방이 더 오래 기다린 방입니다. */
    private Long waitingRoom(Subcategory subcategory, Stance hostStance) {
        return waitingRoom(subcategory, hostStance, userRepository.getReferenceById(newUser()));
    }

    private Long waitingRoom(Subcategory subcategory, Stance hostStance, User host) {
        DebateSession session = sessionRepository.save(DebateSession.createRandom(topic(subcategory)));
        participantRepository.save(DebateParticipant.initiator(session, host, hostStance, LocalDateTime.now()));
        return session.getId();
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId, Role.GUEST);
    }

    private ResultActions propose(String category) throws Exception {
        return mockMvc.perform(get("/api/v1/sessions/proposal").param("category", category)
                .header(HttpHeaders.AUTHORIZATION, bearer(me)));
    }

    private ResultActions reject(Long sessionId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, bearer(me)));
    }

    private ResultActions waiting(String category, int page) throws Exception {
        return mockMvc.perform(get("/api/v1/sessions/waiting").param("category", category)
                .param("page", String.valueOf(page))
                .header(HttpHeaders.AUTHORIZATION, bearer(me)));
    }

    @Nested
    @DisplayName("자동 제안 · 거절")
    class Proposal {

        @Test
        @DisplayName("고른 카테고리부터 돌며 카테고리마다 가장 오래된 방을 제안하고, 거절하면 다음 카테고리, 한 바퀴면 고른 카테고리의 새 주제")
        void rotatesFromChosenCategory() throws Exception {
            topic(Subcategory.AI);
            Long ethicsOld = waitingRoom(Subcategory.GOOD_AND_EVIL, Stance.AGREE);
            waitingRoom(Subcategory.LYING, Stance.DISAGREE);
            Long human = waitingRoom(Subcategory.HUMAN_NATURE, Stance.DISAGREE);

            // 기술과 미래 → 돈과 성공 → 진실과 믿음 → 인간 → 관계 → 윤리 …
            propose("TECH_AND_FUTURE")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.result").value("PROPOSAL"))
                    .andExpect(jsonPath("$.data.sessionId").value(human))
                    .andExpect(jsonPath("$.data.topic.category").value("HUMAN"))
                    .andExpect(jsonPath("$.data.myStance").value("AGREE"));
            reject(human)
                    .andExpect(jsonPath("$.data.result").value("PROPOSAL"))
                    .andExpect(jsonPath("$.data.sessionId").value(ethicsOld))
                    .andExpect(jsonPath("$.data.myStance").value("DISAGREE"));
            reject(ethicsOld)
                    .andExpect(jsonPath("$.data.result").value("NEW_TOPIC"))
                    .andExpect(jsonPath("$.data.topic.category").value("TECH_AND_FUTURE"))
                    .andExpect(jsonPath("$.data.sessionId").doesNotExist())
                    .andExpect(jsonPath("$.data.myStance").doesNotExist());
            assertThat(cursorStore.find(me)).isEmpty();
        }

        @Test
        @DisplayName("대기 방이 없으면 바로 고른 카테고리의 새 주제")
        void newTopicWhenNoRoom() throws Exception {
            topic(Subcategory.WEALTH);

            propose("MONEY_AND_SUCCESS")
                    .andExpect(jsonPath("$.data.result").value("NEW_TOPIC"))
                    .andExpect(jsonPath("$.data.topic.category").value("MONEY_AND_SUCCESS"));
        }

        @Test
        @DisplayName("내 방 · 봇전으로 바뀐 방 · 진행 중인 방은 제안하지 않는다")
        void skipsUnjoinableRooms() throws Exception {
            topic(Subcategory.GOOD_AND_EVIL);
            waitingRoom(Subcategory.GOOD_AND_EVIL, Stance.AGREE, userRepository.getReferenceById(me));
            Long converted = waitingRoom(Subcategory.GOOD_AND_EVIL, Stance.AGREE);
            sessionRepository.convertToAiIfWaiting(converted, LocalDateTime.now());
            Long started = waitingRoom(Subcategory.GOOD_AND_EVIL, Stance.AGREE);
            sessionRepository.startIfWaiting(started, LocalDateTime.now());

            propose("ETHICS").andExpect(jsonPath("$.data.result").value("NEW_TOPIC"));
        }

        @Test
        @DisplayName("다시 부르면 처음부터 다시 돈다")
        void restartsOnPropose() throws Exception {
            Long human = waitingRoom(Subcategory.HUMAN_NATURE, Stance.AGREE);
            waitingRoom(Subcategory.LOVE, Stance.AGREE);
            propose("HUMAN").andExpect(jsonPath("$.data.sessionId").value(human));
            reject(human);

            propose("HUMAN").andExpect(jsonPath("$.data.sessionId").value(human));
        }

        @Test
        @DisplayName("순환 위치가 사라진 뒤 거절하면 거절한 방의 카테고리 다음부터 새로 돈다")
        void rejectAfterCursorExpired() throws Exception {
            Long human = waitingRoom(Subcategory.HUMAN_NATURE, Stance.AGREE);
            Long relationship = waitingRoom(Subcategory.LOVE, Stance.AGREE);
            propose("TRUTH_AND_BELIEF").andExpect(jsonPath("$.data.sessionId").value(human));
            cursorStore.delete(me);

            reject(human).andExpect(jsonPath("$.data.sessionId").value(relationship));
        }

        @Test
        @DisplayName("입장에 성공하면 순환 위치를 지운다")
        void joinForgetsCursor() throws Exception {
            Long human = waitingRoom(Subcategory.HUMAN_NATURE, Stance.AGREE);
            propose("HUMAN").andExpect(jsonPath("$.data.sessionId").value(human));
            assertThat(cursorStore.find(me)).isPresent();

            mockMvc.perform(post("/api/v1/sessions/" + human + "/join").header(HttpHeaders.AUTHORIZATION, bearer(me)))
                    .andExpect(status().isOk());
            joinedSessionIds.add(human);

            assertThat(cursorStore.find(me)).isEmpty();
        }

        @Test
        @DisplayName("없는 방을 거절하면 SESSION_NOT_FOUND, 카테고리 값이 잘못되면 BINDING_ERROR")
        void errors() throws Exception {
            reject(Long.MAX_VALUE)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
            propose("NOT_A_CATEGORY")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BINDING_ERROR"));
        }
    }

    @Nested
    @DisplayName("방 찾기")
    class Waiting {

        @Test
        @DisplayName("카테고리의 대기 방을 오래된 순으로, 내 방은 빼고 카드(방장 · 주제 · 찾는 입장)로 준다")
        void listsOldestFirst() throws Exception {
            Long older = waitingRoom(Subcategory.GOOD_AND_EVIL, Stance.AGREE);
            Long newer = waitingRoom(Subcategory.LYING, Stance.DISAGREE);
            waitingRoom(Subcategory.GOOD_AND_EVIL, Stance.AGREE, userRepository.getReferenceById(me));
            waitingRoom(Subcategory.HUMAN_NATURE, Stance.AGREE);

            waiting("ETHICS", 0)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rooms.length()").value(2))
                    .andExpect(jsonPath("$.data.rooms[0].sessionId").value(older))
                    .andExpect(jsonPath("$.data.rooms[0].wantedStance").value("DISAGREE"))
                    .andExpect(jsonPath("$.data.rooms[0].host.nickname").isNotEmpty())
                    .andExpect(jsonPath("$.data.rooms[0].host.philosopherType").doesNotExist())
                    .andExpect(jsonPath("$.data.rooms[0].topic.statement").value("주제 GOOD_AND_EVIL"))
                    .andExpect(jsonPath("$.data.rooms[1].sessionId").value(newer))
                    .andExpect(jsonPath("$.data.rooms[1].wantedStance").value("AGREE"))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("한 페이지 20개, 남으면 hasNext")
        void pages() throws Exception {
            for (int i = 0; i < 21; i++) {
                waitingRoom(Subcategory.WEALTH, Stance.AGREE);
            }

            waiting("MONEY_AND_SUCCESS", 0)
                    .andExpect(jsonPath("$.data.rooms.length()").value(20))
                    .andExpect(jsonPath("$.data.hasNext").value(true));
            waiting("MONEY_AND_SUCCESS", 1)
                    .andExpect(jsonPath("$.data.rooms.length()").value(1))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("page 가 음수면 VALIDATION_FAILED")
        void negativePage() throws Exception {
            waiting("ETHICS", -1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }
}
