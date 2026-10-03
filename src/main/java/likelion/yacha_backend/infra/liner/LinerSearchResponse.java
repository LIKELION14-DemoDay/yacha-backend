package likelion.yacha_backend.infra.liner;

import java.util.List;

public record LinerSearchResponse(
        String requestId,
        List<LinerSearchResult> results,
        Integer totalCount
) {
}