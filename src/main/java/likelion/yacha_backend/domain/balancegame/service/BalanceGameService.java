
package likelion.yacha_backend.domain.balancegame.service;

import java.util.List;
import likelion.yacha_backend.domain.balancegame.content.BalanceQuestions;
import likelion.yacha_backend.domain.balancegame.dto.BalanceGameAnswerRequest;
import likelion.yacha_backend.domain.balancegame.dto.PhilosopherResponse;
import likelion.yacha_backend.domain.balancegame.dto.QuestionResponse;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.domain.user.service.PhilosopherImageUrlService;
import likelion.yacha_backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 밸런스 게임 — 질문 12개로 철학 유형을 정합니다.
 *
 * 질문 · 배점 · 결과 문구는 코드에서 관리하고,
 * 사용자에게는 철학자 유형과 진행 상태만 저장합니다.
 */
@Service
@RequiredArgsConstructor
public class BalanceGameService {

    private final UserService userService;
    private final PhilosopherImageUrlService philosopherImageUrlService;

    public List<QuestionResponse> questions() {
        return BalanceQuestions.ALL.stream()
                .map(QuestionResponse::from)
                .toList();
    }

    /**
     * 답변으로 철학자 유형을 결정하고 저장합니다.
     * 다시 진행하면 기존 결과를 덮어씁니다.
     */
    public PhilosopherResponse submit(
            Long userId,
            BalanceGameAnswerRequest request
    ) {
        PhilosopherType type = BalanceGameScorer.decide(
                BalanceQuestions.ALL,
                request.answers()
        );

        userService.completeBalanceGame(userId, type);

        return PhilosopherResponse.of(
                type,
                philosopherImageUrlService.readUrl(type)
        );
    }

    /**
     * 로그인 전 미리보기입니다.
     * 철학자 유형만 결정하고 DB에는 저장하지 않습니다.
     */
    public PhilosopherResponse preview(
            BalanceGameAnswerRequest request
    ) {
        PhilosopherType type = BalanceGameScorer.decide(
                BalanceQuestions.ALL,
                request.answers()
        );

        return PhilosopherResponse.of(
                type,
                philosopherImageUrlService.readUrl(type)
        );
    }

    /**
     * 밸런스 게임을 건너뜁니다.
     * 이미 결과가 있으면 기존 결과를 유지합니다.
     */
    public void skip(Long userId) {
        userService.skipBalanceGame(userId);
    }

    /**
     * 공유 링크에서 사용할 철학자 유형 설명입니다.
     */
    public PhilosopherResponse describe(PhilosopherType type) {
        return PhilosopherResponse.of(
                type,
                philosopherImageUrlService.readUrl(type)
        );
    }
}
