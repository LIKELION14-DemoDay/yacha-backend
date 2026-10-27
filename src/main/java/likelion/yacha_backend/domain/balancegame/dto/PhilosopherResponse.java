package likelion.yacha_backend.domain.balancegame.dto;

import java.util.List;
import likelion.yacha_backend.domain.balancegame.content.PhilosopherProfiles;
import likelion.yacha_backend.domain.balancegame.content.PhilosopherProfiles.Section;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;

/**
 * 결과 화면 · 공유 화면의 유형 설명.
 *
 * @param name     "니체" 처럼 화면 이름 ("당신은 니체 유형입니다")
 * @param alias    한 줄 별칭
 * @param sections 소제목이 있는 성향 3개. 소제목은 앞에 "■ " 가 붙고 유형마다 다름 (니체: ■ 나만의 기준 · ■ 성장을 위한 도전 · ■ 다름을 선택하는 용기)
 */
public record PhilosopherResponse(PhilosopherType type, String name, String alias, List<Section> sections) {

    public static PhilosopherResponse of(PhilosopherType type) {
        return new PhilosopherResponse(type, type.getDisplayName(), type.getAlias(), PhilosopherProfiles.of(type));
    }
}
