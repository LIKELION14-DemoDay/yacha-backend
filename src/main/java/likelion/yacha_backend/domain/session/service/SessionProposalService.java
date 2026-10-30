package likelion.yacha_backend.domain.session.service;

import java.util.List;
import java.util.Optional;
import likelion.yacha_backend.domain.session.dto.ProposalResponse;
import likelion.yacha_backend.domain.session.dto.WaitingRoomsResponse;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.session.repository.ProposalCursor;
import likelion.yacha_backend.domain.session.repository.ProposalCursorStore;
import likelion.yacha_backend.domain.topic.dto.TopicResponse;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.service.TopicService;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import likelion.yacha_backend.domain.user.service.PhilosopherImageUrlService;
/**
 * 남이 만든 대기 방 찾기 — 자동 제안 · 제안 거절 · 방 찾기 (명세 2-3-2). 입장은 {@code POST /sessions/{id}/join} 입니다.
 *
 * <p><b>자동 제안</b>은 고른 카테고리부터 8개를 {@link Category} 선언 순서로 돌며, 카테고리마다 가장 오래된 대기 방
 * 하나를 보여줍니다. 대기 방이 없는 카테고리는 건너뛰고, 한 바퀴를 돌면 고른 카테고리의 새 주제({@code NEW_TOPIC})를
 * 줍니다. 어디까지 봤는지는 사용자별로 {@link ProposalCursorStore} 에 둡니다.
 *
 * <p>방을 미리 잡아 두지 않습니다. 제안한 방에 다른 사람이 먼저 들어가면 입장이 {@code SESSION_NOT_WAITING} 이 되고,
 * 프론트가 제안을 다시 요청합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionProposalService {

    /** 방 찾기 한 페이지의 방 수. */
    static final int WAITING_PAGE_SIZE = 20;

    private static final Category[] CATEGORIES = Category.values();
    private final PhilosopherImageUrlService philosopherImageUrlService;
    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;
    private final ProposalCursorStore cursorStore;
    private final TopicService topicService;

    /** 자동 제안을 시작합니다. 전에 돌던 위치는 버리고 고른 카테고리부터 다시 돕니다. */
    public ProposalResponse propose(Long userId, Category category) {
        return proposeFrom(userId, new ProposalCursor(category, 0));
    }

    /**
     * 제안을 거절하고 다음 카테고리의 방을 봅니다. 위치가 만료됐으면 거절한 방의 카테고리 다음부터 새로 돕니다.
     *
     * @throws BusinessException {@code SESSION_NOT_FOUND} 없는 방
     */
    public ProposalResponse reject(Long userId, Long sessionId) {
        DebateSession rejected = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(SessionErrorCode.SESSION_NOT_FOUND));
        ProposalCursor cursor = cursorStore.find(userId)
                .orElseGet(() -> new ProposalCursor(rejected.getCategory(), 1));
        return proposeFrom(userId, cursor);
    }

    /** 입장에 성공했습니다. 다음 제안은 처음부터 돕니다. */
    public void forget(Long userId) {
        cursorStore.delete(userId);
    }

    /**
     * 방 찾기 — 카테고리의 대기 방을 오래된 순으로. 내 방은 뺍니다.
     *
     * @param page 0 부터. 음수면 {@code VALIDATION_FAILED}
     */

    public WaitingRoomsResponse findWaiting(
            Long userId,
            Category category,
            int page
    ) {
        if (page < 0) {
            throw new BusinessException(
                    GlobalErrorCode.VALIDATION_FAILED
            );
        }

        return WaitingRoomsResponse.from(
                participantRepository.findWaitingHosts(
                        category,
                        userId,
                        PageRequest.of(page, WAITING_PAGE_SIZE)
                ),
                philosopherImageUrlService::readUrl
        );
    }


    private ProposalResponse proposeFrom(Long userId, ProposalCursor cursor) {
        for (int offset = cursor.nextOffset(); offset < CATEGORIES.length; offset++) {
            Category category = CATEGORIES[(cursor.start().ordinal() + offset) % CATEGORIES.length];
            Optional<DebateParticipant> host = oldestHost(category, userId);
            if (host.isPresent()) {
                cursorStore.save(userId, new ProposalCursor(cursor.start(), offset + 1));
                DebateParticipant found = host.get();
                return ProposalResponse.proposal(found.getSession().getId(),
                        TopicResponse.from(found.getSession().getTopic()), found.getStance().opposite());
            }
        }
        // 한 바퀴를 다 돌았습니다. 다음 /proposal 은 처음부터 돕니다.
        cursorStore.delete(userId);
        return ProposalResponse.newTopic(topicService.pickRandom(cursor.start(), List.of()));
    }

    private Optional<DebateParticipant> oldestHost(Category category, Long userId) {
        return participantRepository.findWaitingHosts(category, userId, PageRequest.of(0, 1)).stream().findFirst();
    }
}
