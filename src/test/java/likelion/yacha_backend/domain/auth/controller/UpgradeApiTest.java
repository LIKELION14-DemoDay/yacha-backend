package likelion.yacha_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.auth.dto.UpgradeRequest;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.auth.service.AuthService;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("게스트 → 회원 승격")
class UpgradeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private DebateParticipantRepository participantRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String guestAccessToken;
    private Cookie guestRefreshCookie;
    private Integer guestUserId;

    /** 참가 기록에 쓰는 고정 시각. 실행할 때마다 달라지지 않게 하려는 것으로, 이 값 자체를 검증하지는 않습니다. */
    private static final LocalDateTime GAME_TIME = LocalDateTime.of(2026, 10, 1, 12, 0);

    private static final String UPGRADE_BODY = """
            {"email": "Upgraded@Example.com", "password": "password123", "nickname": "멋사"}
            """;

    @BeforeEach
    void createGuest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();
        String body = result.getResponse().getContentAsString();

        guestAccessToken = JsonPath.read(body, "$.data.accessToken");
        guestUserId = JsonPath.read(body, "$.data.userId");
        guestRefreshCookie = result.getResponse().getCookie("refreshToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @DisplayName("승격하면 같은 계정이 회원이 됨 (userId 유지)")
    void upgradeKeepsSameAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andExpect(status().isOk())
                // 새 계정이 아니라 같은 행이어야 합니다. id 가 바뀌면 대기 · 진행 중인 게임에서 참가자로 인정되지 않습니다.
                .andExpect(jsonPath("$.data.userId").value(guestUserId))
                .andExpect(jsonPath("$.data.isGuest").value(false))
                // 행은 그대로이고 닉네임도 가입 폼에서 이미 받았으므로 새 계정으로 보지 않음
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.nickname").value("멋사"));
    }

    @Test
    @DisplayName("게스트 때 끝난 경기는 연결이 끊기고, 진행 중인 경기는 그대로 (비회원 결과는 전적 제외)")
    void detachesEndedGuestGamesOnUpgrade() throws Exception {
        LocalDateTime now = GAME_TIME;
        User guest = userRepository.getReferenceById(guestUserId.longValue());
        User other = userRepository.save(User.createMember("other@example.com", "encoded", "상대"));

        DebateSession ended = sessionRepository.save(DebateSession.createRandom(Category.ETHICS, 1L));
        Long endedParticipant = participantRepository.save(
                DebateParticipant.initiator(ended, guest, Stance.AGREE, now)).getId();
        participantRepository.save(DebateParticipant.opponent(ended, other, Stance.DISAGREE, now));
        sessionRepository.startIfWaiting(ended.getId(), now);
        sessionRepository.finishIfInProgress(ended.getId(), FinishReason.COMPLETED, now);

        DebateSession playing = sessionRepository.save(DebateSession.createRandom(Category.ETHICS, 1L));
        Long playingParticipant = participantRepository.save(
                DebateParticipant.initiator(playing, guest, Stance.AGREE, now)).getId();
        sessionRepository.startIfWaiting(playing.getId(), now);

        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andExpect(status().isOk());

        assertThat(participantRepository.findById(endedParticipant)).get()
                .extracting(DebateParticipant::getUser).isNull();
        // 상대(회원)의 기록은 그대로
        assertThat(participantRepository.existsBySession_IdAndUser_Id(ended.getId(), other.getId())).isTrue();
        // 진행 중인 경기는 승격한 회원이 계속 참가자
        assertThat(participantRepository.existsBySession_IdAndUser_Id(playing.getId(), guestUserId.longValue())).isTrue();
        assertThat(participantRepository.findById(playingParticipant)).isPresent();
    }

    @Test
    @DisplayName("승격하는 사이 정리 작업이 계정을 지우면 500 이 아니라 USER_NOT_FOUND")
    void accountDeletedDuringUpgrade() {
        Long userId = guestUserId.longValue();
        // 승격이 계정을 읽어 둔 상태 (같은 트랜잭션이라 이후 findById 는 이 객체를 그대로 씀)
        userRepository.findById(userId).orElseThrow();
        // 그 사이 정리 작업이 행을 지움. 읽어 둔 객체는 모르게 SQL 로 바로 지움
        jdbcTemplate.update("delete from users where id = ?", userId);

        assertThatThrownBy(() -> authService.upgrade(userId,
                new UpgradeRequest("late@example.com", "password123", "늦은승격")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("승격 후 이메일이 소문자로 저장되어 그 계정으로 로그인")
    void canLoginAfterUpgrade() throws Exception {
        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "upgraded@example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(guestUserId));
    }

    @Test
    @DisplayName("승격하면 토큰이 새로 발급되어 예전 리프레시 쿠키는 쓸 수 없음")
    void oldRefreshTokenIsInvalidatedAfterUpgrade() throws Exception {
        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(guestRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("승격 후 내 정보에 이메일이 보이고 게스트가 아님")
    void myInfoReflectsUpgrade() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andReturn();

        String newAccessToken = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.accessToken");

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
                .andExpect(jsonPath("$.data.email").value("upgraded@example.com"))
                .andExpect(jsonPath("$.data.isGuest").value(false));
    }

    @Test
    @DisplayName("이미 회원이면 409 ALREADY_MEMBER")
    void rejectsAlreadyMember() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andReturn();

        String newAccessToken = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.accessToken");

        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "another@example.com", "password": "password123", "nickname": "멋사"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_MEMBER"));
    }

    @Test
    @DisplayName("다른 사람이 쓰는 이메일이면 409 EMAIL_ALREADY_EXISTS")
    void rejectsDuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "taken@example.com", "password": "password123", "nickname": "먼저"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "taken@example.com", "password": "password123", "nickname": "멋사"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andExpect(status().isUnauthorized());
    }
}
