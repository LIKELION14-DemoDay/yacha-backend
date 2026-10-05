package likelion.yacha_backend.domain.session.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.session.service.SessionAccessService;
import likelion.yacha_backend.domain.session.service.SessionTopicSubscriptionAuthorizer;
import likelion.yacha_backend.domain.session.service.SpectateProperties;
import likelion.yacha_backend.global.security.jwt.AuthUser;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관전 스위치가 꺼진 기본 설정(10/5 관전 미사용). 진행 중인 랜덤 사람전이어도 참가자만 토론방을 봅니다.
 * 스위치를 켠 관전 동작은 {@link SessionApiTest} · {@link GameStompTest} 가 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SessionFixture.class)
@DisplayName("관전 스위치 꺼짐 (기본값) — 참가자만 토론방을 본다")
class SpectateDisabledTest {

    private static final long IN_PROGRESS = 90;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private SpectateProperties spectateProperties;

    @Autowired
    private SessionTopicSubscriptionAuthorizer subscriptionAuthorizer;

    @Autowired
    private DebateSessionRepository sessionRepository;

    private Room room;

    @AfterEach
    void removeGame() {
        fixture.removeGame(room);
    }

    private ResultActions getAs(Long userId, Role role, String path) throws Exception {
        return mockMvc.perform(get("/api/v1/sessions/" + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(userId, role)));
    }

    @Test
    @DisplayName("application.yaml 의 관전 스위치는 꺼져 있다")
    void disabledByDefault() {
        assertThat(spectateProperties.enabled()).isFalse();
    }

    @Test
    @DisplayName("참가자가 아니면 회원 · 게스트 모두 상태 · 메시지를 조회할 수 없다 (NOT_PARTICIPANT)")
    void nonParticipantCannotRead() throws Exception {
        room = fixture.randomHuman(IN_PROGRESS);
        Long stranger = fixture.newUser();

        for (Role role : new Role[]{Role.USER, Role.GUEST}) {
            getAs(stranger, role, room.sessionId() + "/state")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
            getAs(stranger, role, room.sessionId() + "/messages")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
        }
    }

    @Test
    @DisplayName("참가자가 아니면 토론방을 구독할 수 없고, 참가자는 구독할 수 있다")
    void onlyParticipantSubscribes() {
        room = fixture.randomHuman(IN_PROGRESS);
        String destination = "/topic/sessions/" + room.sessionId();

        assertThat(subscriptionAuthorizer.canSubscribe(new AuthUser(fixture.newUser(), Role.USER), destination))
                .isFalse();
        assertThat(subscriptionAuthorizer.canSubscribe(new AuthUser(room.hostUserId(), Role.USER), destination))
                .isTrue();
        assertThat(subscriptionAuthorizer.canSubscribe(new AuthUser(room.opponentUserId(), Role.GUEST), destination))
                .isTrue();
    }

    @Test
    @DisplayName("참가자는 그대로 상태 · 메시지를 조회한다")
    void participantStillReads() throws Exception {
        room = fixture.randomHuman(IN_PROGRESS);

        getAs(room.hostUserId(), Role.USER, room.sessionId() + "/state")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myParticipantId").value(room.hostParticipantId()));
        getAs(room.opponentUserId(), Role.USER, room.sessionId() + "/messages")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("관전 규칙(진행 중인 랜덤 사람전)은 코드에 남아 있다 — 스위치만 꺼져 있다")
    void spectateRuleIsKept() {
        room = fixture.randomHuman(IN_PROGRESS);

        assertThat(SessionAccessService.isSpectatable(sessionRepository.findById(room.sessionId()).orElseThrow()))
                .isTrue();
    }
}
