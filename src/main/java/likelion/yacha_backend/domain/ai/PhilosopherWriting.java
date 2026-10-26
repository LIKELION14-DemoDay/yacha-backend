package likelion.yacha_backend.domain.ai;

import java.util.List;
import java.util.Objects;

/**
 * AI가 만든 1분 철학의 철학자 한 명의 글
 * 엔티티를 쓰지 않아 AI 구현이 JPA에 묶이지 않음 ({@link AiTopic}과 같은 이유)
 *
 * @param philosopherName 철학자 이름 (예: 아리스토텔레스)
 * @param sections        소제목 + 내용 묶음. 위에서부터 차례로
 */
public record PhilosopherWriting(String philosopherName, List<Section> sections) {

    public PhilosopherWriting {
        Objects.requireNonNull(philosopherName, "philosopherName");
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
    }

    public record Section(String subtitle, String body) {

        public Section {
            Objects.requireNonNull(subtitle, "subtitle");
            Objects.requireNonNull(body, "body");
        }
    }
}
