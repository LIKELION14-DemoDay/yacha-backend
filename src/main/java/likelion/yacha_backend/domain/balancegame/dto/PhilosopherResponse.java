package likelion.yacha_backend.domain.balancegame.dto;

import likelion.yacha_backend.domain.balancegame.content.PhilosopherProfiles;
import likelion.yacha_backend.domain.balancegame.content.PhilosopherProfiles.Profile;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;

/**
 * 결과 화면 · 공유 화면의 유형 설명.
 *
 * @param name      "니체" 처럼 화면 이름 ("당신은 니체 유형입니다")
 * @param alias     한 줄 별칭
 * @param standard  {나만의 기준}. 단락은 빈 줄({@code \n\n})로 나뉩니다
 * @param challenge {성장을 위한 도전}
 */
public record PhilosopherResponse(PhilosopherType type, String name, String alias, String standard, String challenge) {

    public static PhilosopherResponse of(PhilosopherType type) {
        Profile profile = PhilosopherProfiles.of(type);
        return new PhilosopherResponse(type, type.getDisplayName(), type.getAlias(),
                profile.standard(), profile.challenge());
    }
}
