package likelion.yacha_backend.infra.liner;

public record LinerSearchResult(
        String title,
        String url,
        String hostname,
        String faviconUrl,
        String description,
        String date
) {
}