package likelion.yacha_backend.domain.heart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import likelion.yacha_backend.domain.heart.repository.HeartUseRepository;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.session.service.WaitTimerService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하트 조회 · 게임 시작 때 차감 · 0개일 때 거절. 돌려주기는 {@code HeartRefundTest} 가 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SessionFixture.class)
@DisplayName("하트 API — 조회 · 게임 시작 때 차감 · HEART_EXHAUSTED")
class HeartApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private HeartUseRepository heartUseRepository;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private WaitTimerService waitTimerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private ResultActions me(Long userId, Role role) throws Exception {
        return mockMvc.perform(get("/api/v1/hearts/me").header(HttpHeaders.AUTHORIZATION, bearer(userId, role)));
    }

    private int hearts(Long userId) throws Exception {
        String body = me(userId, Role.USER).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.hearts");
    }

    /** 하트를 0개로 만듭니다 (방금 다 쓴 상태). */
    private void exhaust(Long userId) {
        jdbcTemplate.update("insert into user_heart (user_id, hearts, charged_at) values (?, 0, ?)",
                userId, LocalDateTime.now());
    }

    private ResultActions create(Long userId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomType\":\"RANDOM\",\"topicId\":%d,\"stance\":\"AGREE\"}"
                        .formatted(fixture.topic().getId())));
    }

    private Long createdId(ResultActions result) throws Exception {
        Long sessionId = ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(),
                "$.data.sessionId")).longValue();
        sessionIds.add(sessionId);
        return sessionId;
    }

    private ResultActions join(Long userId, Long sessionId) throws Exception {
        sessionIds.add(sessionId);
        return mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/join")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, Role.USER)));
    }

    private String createFriend(Long userId) throws Exception {
        fixture.topic();
        String body = mockMvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"FRIEND\",\"category\":\"ETHICS\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        sessionIds.add(((Number) JsonPath.read(body, "$.data.sessionId")).longValue());
        return JsonPath.read(body, "$.data.inviteCode");
    }

    private ResultActions joinFriend(Long userId, String inviteCode) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/invite/" + inviteCode + "/join")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, Role.USER)));
    }

    private SessionStatus statusOf(Long sessionId) {
        return sessionRepository.findById(sessionId).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("처음에는 하트가 가득 차 있고 다음 충전 시각이 없다 (게스트도 조회 가능)")
    void startsFull() throws Exception {
        me(fixture.newUser(), Role.GUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hearts").value(3))
                .andExpect(jsonPath("$.data.max").value(3))
                .andExpect(jsonPath("$.data.nextChargeAt").doesNotExist());
    }

    @Test
    @DisplayName("랜덤 방에 들어가면 방장 · 입장한 사람 모두 1개씩 깎이고, 다음 충전 시각이 생긴다")
    void joinUsesBothHearts() throws Exception {
        Room room = fixture.waitingRandom();
        Long joiner = fixture.newUser();

        join(joiner, room.sessionId()).andExpect(status().isOk());

        assertThat(hearts(room.hostUserId())).isEqualTo(2);
        me(joiner, Role.USER)
                .andExpect(jsonPath("$.data.hearts").value(2))
                .andExpect(jsonPath("$.data.nextChargeAt").isString());
        assertThat(heartUseRepository.findUnrefundedUserIds(room.sessionId()))
                .containsExactlyInAnyOrder(room.hostUserId(), joiner);
    }

    @Test
    @DisplayName("봇전으로 바꾸면 방장만 1개 깎인다")
    void botUsesHostHeartOnly() throws Exception {
        Room room = fixture.waitingRandom(30);
        sessionIds.add(room.sessionId());

        mockMvc.perform(post("/api/v1/sessions/" + room.sessionId() + "/ai")
                        .header(HttpHeaders.AUTHORIZATION, bearer(room.hostUserId(), Role.USER)))
                .andExpect(status().isOk());

        assertThat(hearts(room.hostUserId())).isEqualTo(2);
        assertThat(heartUseRepository.findUnrefundedUserIds(room.sessionId())).containsExactly(room.hostUserId());
    }

    @Test
    @DisplayName("친구 방도 들어가면 두 사람 모두 1개씩 깎인다")
    void friendRoomUsesBothHearts() throws Exception {
        Long host = fixture.newMember();
        Long friend = fixture.newUser();
        String inviteCode = createFriend(host);

        joinFriend(friend, inviteCode).andExpect(status().isOk());

        assertThat(hearts(host)).isEqualTo(2);
        assertThat(hearts(friend)).isEqualTo(2);
    }

    @Test
    @DisplayName("방을 만들고 취소하면 하트는 그대로다")
    void createAndCancelKeepHearts() throws Exception {
        Long host = fixture.newUser();
        Long sessionId = createdId(create(host).andExpect(status().isOk()));

        mockMvc.perform(delete("/api/v1/sessions/" + sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(host, Role.USER)))
                .andExpect(status().isOk());

        assertThat(hearts(host)).isEqualTo(3);
    }

    @Test
    @DisplayName("하트가 0개면 방을 만들 수 없다 (HEART_EXHAUSTED)")
    void cannotCreateWithoutHearts() throws Exception {
        Long host = fixture.newUser();
        exhaust(host);

        create(host)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HEART_EXHAUSTED"));
    }

    @Test
    @DisplayName("하트가 0개면 랜덤 방에 들어갈 수 없고, 방은 계속 기다리며 방장 하트도 그대로다")
    void cannotJoinWithoutHearts() throws Exception {
        Room room = fixture.waitingRandom();
        Long joiner = fixture.newUser();
        exhaust(joiner);

        join(joiner, room.sessionId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HEART_EXHAUSTED"));

        assertThat(statusOf(room.sessionId())).isEqualTo(SessionStatus.WAITING);
        assertThat(hearts(room.hostUserId())).isEqualTo(3);
        assertThat(heartUseRepository.findUnrefundedUserIds(room.sessionId())).isEmpty();
    }

    @Test
    @DisplayName("하트가 0개면 친구 방에 들어갈 수 없다")
    void cannotJoinFriendWithoutHearts() throws Exception {
        Long host = fixture.newMember();
        Long friend = fixture.newUser();
        String inviteCode = createFriend(host);
        exhaust(friend);

        joinFriend(friend, inviteCode)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HEART_EXHAUSTED"));

        assertThat(hearts(host)).isEqualTo(3);
    }
}
