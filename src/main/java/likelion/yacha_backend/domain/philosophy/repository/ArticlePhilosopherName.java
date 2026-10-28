package likelion.yacha_backend.domain.philosophy.repository;

/** 목록 카드에 쓰는 글의 철학자 이름 하나 */
public record ArticlePhilosopherName(Long articleId, String philosopherName) {
}
