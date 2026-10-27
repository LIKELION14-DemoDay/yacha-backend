package likelion.yacha_backend.domain.balancegame.service;

import java.util.List;
import likelion.yacha_backend.domain.balancegame.content.BalanceQuestions;
import likelion.yacha_backend.domain.balancegame.dto.BalanceGameAnswerRequest;
import likelion.yacha_backend.domain.balancegame.dto.PhilosopherResponse;
import likelion.yacha_backend.domain.balancegame.dto.QuestionResponse;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 밸런스 게임 — 질문 12개로 철학 유형을 정합니다 (노션 "밸런스게임 › 철학자 기준").
 *
 * <p>질문 · 배점 · 결과 문구는 코드에 있고({@code content} 패키지), 사용자에게 남는 것은 유형과 진행 상태뿐입니다.
 * 사용자 행은 사용자 도메인의 서비스로 바꿉니다 (다른 도메인의 데이터는 서비스로 다루는 팀 규칙).
 */
@Service
@RequiredArgsConstructor
public class BalanceGameService {

    private final UserService userService;

    public List<QuestionResponse> questions() {
        return BalanceQuestions.ALL.stream().map(QuestionResponse::from).toList();
    }

    /** 답으로 유형을 정해 저장하고 결과 화면 내용을 돌려줍니다. 다시 하면 덮어씁니다. */
    public PhilosopherResponse submit(Long userId, BalanceGameAnswerRequest request) {
        PhilosopherType type = BalanceGameScorer.decide(BalanceQuestions.ALL, request.answers());
        userService.completeBalanceGame(userId, type);
        return PhilosopherResponse.of(type);
    }

    /** 건너뛰기. 다음부터 홈에서 띄우지 않습니다. 이미 결과가 있으면 그대로 둡니다. */
    public void skip(Long userId) {
        userService.skipBalanceGame(userId);
    }

    /** 공유 링크용 — 유형 설명만. 누구의 결과인지는 담지 않습니다. */
    public PhilosopherResponse describe(PhilosopherType type) {
        return PhilosopherResponse.of(type);
    }
}
