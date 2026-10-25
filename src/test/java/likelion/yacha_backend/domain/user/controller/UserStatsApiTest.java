package likelion.yacha_backend.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateResult;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지 전투 기록 — {@code GET /users/me} 의 {@code stats} (승리 · 패배 수, 무승부 제외). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("내 정보 — 전투 기록(승리 · 패배 수)")
class UserStatsApiTest {

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

    private User member() {
        String name = "m" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.createMember(name + "@test.com", "encoded", name));
    }

    /** 끝난 판 하나. {@code result} 가 null 이면 승패가 없는 판(무효 · 판정 실패)입니다. */
    private void played(User user, DebateResult result) {
        Topic topic = topicRepository.save(Topic.create(Subcategory.GOOD_AND_EVIL, "주제", "그렇다", "아니다"));
        DebateSession session = sessionRepository.save(DebateSession.createRandom(topic));
        DebateParticipant participant = DebateParticipant.initiator(session, user, Stance.AGREE, LocalDateTime.now());
        if (result != null) {
            participant.recordResult(result);
        }
        participantRepository.save(participant);
    }

    @Test
    @DisplayName("내 승리 · 패배 수만 세고, 무승부 · 승패 없는 판 · 남의 기록은 세지 않는다")
    void countsWinsAndLosses() throws Exception {
        User me = member();
        User other = member();
        played(me, DebateResult.WIN);
        played(me, DebateResult.WIN);
        played(me, DebateResult.LOSE);
        played(me, DebateResult.DRAW);
        played(me, null);
        played(other, DebateResult.WIN);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtTokenProvider.createAccessToken(me.getId(), Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.wins").value(2))
                .andExpect(jsonPath("$.data.stats.losses").value(1))
                .andExpect(jsonPath("$.data.stats.total").doesNotExist())
                .andExpect(jsonPath("$.data.stats.draws").doesNotExist());
    }
}
