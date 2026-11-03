package likelion.yacha_backend.domain.session.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.session.service.WaitTimerService;
import likelion.yacha_backend.domain.session.service.WaitingSessionRecovery;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SessionFixture.class)
@DisplayName("친구 초대 코드 생성·입장·만료 API")
class FriendInviteMatchApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionFixture fixture;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DebateSessionRepository sessionRepository;
    @Autowired private DebateParticipantRepository participantRepository;
    @Autowired private GameRegistry gameRegistry;
    @Autowired private WaitTimerService waitTimerService;
    @Autowired private WaitingSessionRecovery waitingSessionRecovery;

    // DB는 테스트 종료 때 롤백되지만, 메모리 게임과 타이머는 직접 정리한다.
    private final List<Long> sessionIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long sessionId : sessionIds) {
            waitTimerService.cancel(sessionId);
            gameRegistry.remove(sessionId);
        }
    }

    private String bearer(Long userId, Role role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId, role);
    }

    private String createFriend(Long userId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"FRIEND\",\"category\":\"ETHICS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNumber())
                .andExpect(jsonPath("$.data.inviteCode").isString())
                .andExpect(jsonPath("$.data.expiresAt").isString())
                .andReturn().getResponse().getContentAsString();

        sessionIds.add(((Number) JsonPath.read(body, "$.data.sessionId")).longValue());
        return body;
    }

    @Test
    @DisplayName("회원이 친구 방을 만들면 초대 코드가 발급되고 주제·입장은 비어 있다")
    void createsFriendRoom() throws Exception {
        Long hostId = fixture.newMember();
        String body = createFriend(hostId);
        Long sessionId = ((Number) JsonPath.read(body, "$.data.sessionId")).longValue();
        String inviteCode = JsonPath.read(body, "$.data.inviteCode");

        assertThat(inviteCode).hasSize(10);
        DebateSession session = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getRoomType()).isEqualTo(RoomType.FRIEND);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.WAITING);
        assertThat(session.getCategory()).isEqualTo(Category.ETHICS);
        assertThat(session.getTopic()).isNull();
        assertThat(session.getInviteCode()).isEqualTo(inviteCode);
        DebateParticipant host = participantRepository
                .findBySession_IdAndUser_Id(sessionId, hostId).orElseThrow();
        assertThat(host.getStance()).isNull();
    }

    @Test
    @DisplayName("친구가 입장하면 주제·반대 입장이 배정되고 세 번째 입장은 거부한다")
    void friendJoinsAndStartsGame() throws Exception {
        fixture.topic(); // ETHICS 카테고리에 활성 주제가 있어야 매칭 가능
        Long hostId = fixture.newMember();
        String body = createFriend(hostId);
        Long sessionId = ((Number) JsonPath.read(body, "$.data.sessionId")).longValue();
        String inviteCode = JsonPath.read(body, "$.data.inviteCode");

        Long friendId = fixture.newUser(); // 초대받은 사람은 게스트여도 가능
        mockMvc.perform(post("/api/v1/sessions/invite/{code}/join", inviteCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(friendId, Role.GUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId));

        DebateSession session = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.getTopic()).isNotNull();
        assertThat(session.getTopic().getCategory()).isEqualTo(Category.ETHICS);
        DebateParticipant host = participantRepository
                .findBySession_IdAndUser_Id(sessionId, hostId).orElseThrow();
        DebateParticipant friend = participantRepository
                .findBySession_IdAndUser_Id(sessionId, friendId).orElseThrow();
        assertThat(host.getStance()).isNotNull();
        assertThat(friend.getStance()).isEqualTo(host.getStance().opposite());
        assertThat(gameRegistry.find(sessionId)).isPresent();

        Long thirdUserId = fixture.newUser();
        mockMvc.perform(post("/api/v1/sessions/invite/{code}/join", inviteCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(thirdUserId, Role.GUEST)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_WAITING"));
    }

    @Test
    @DisplayName("존재하지 않는 초대 코드는 거부한다")
    void invalidInviteCode() throws Exception {
        Long guestId = fixture.newUser();
        mockMvc.perform(post("/api/v1/sessions/invite/{code}/join", "NOTEXIST99")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestId, Role.GUEST)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVALID_INVITE_CODE"));
    }

    @Test
    @DisplayName("게스트는 친구 방을 생성할 수 없다")
    void guestCannotCreateFriendRoom() throws Exception {
        Long guestId = fixture.newUser();
        mockMvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestId, Role.GUEST))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"FRIEND\",\"category\":\"ETHICS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GUEST_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("만료된 초대 코드는 거부하고 주기 정리로 방을 취소한다")
    void expiredFriendRoom() throws Exception {
        Long hostId = fixture.newMember();
        String body = createFriend(hostId);
        Long sessionId = ((Number) JsonPath.read(body, "$.data.sessionId")).longValue();
        String inviteCode = JsonPath.read(body, "$.data.inviteCode");
        fixture.backdate(sessionId, 12 * 60);

        Long guestId = fixture.newUser();
        mockMvc.perform(post("/api/v1/sessions/invite/{code}/join", inviteCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestId, Role.GUEST)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("INVITE_EXPIRED"));

        waitingSessionRecovery.sweepExpired();
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.CANCELLED);
    }
}
