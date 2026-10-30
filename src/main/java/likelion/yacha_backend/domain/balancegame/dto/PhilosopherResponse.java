
package likelion.yacha_backend.domain.balancegame.dto;

import java.util.List;
import likelion.yacha_backend.domain.balancegame.content.PhilosopherProfiles;
import likelion.yacha_backend.domain.balancegame.content.PhilosopherProfiles.Section;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;

/**
 * 밸런스게임 결과 및 공유 화면의 철학자 유형 설명.
 */
public record PhilosopherResponse(
        PhilosopherType type,
        String name,
        String alias,
        List<Section> sections,
        String imageUrl
) {

    public static PhilosopherResponse of(
            PhilosopherType type,
            String imageUrl
    ) {
        return new PhilosopherResponse(
                type,
                type.getDisplayName(),
                type.getAlias(),
                PhilosopherProfiles.of(type),
                imageUrl
        );
    }
}
