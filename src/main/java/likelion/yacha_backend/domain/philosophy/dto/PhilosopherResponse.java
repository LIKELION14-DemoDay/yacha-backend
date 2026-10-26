package likelion.yacha_backend.domain.philosophy.dto;

import java.util.List;
import likelion.yacha_backend.domain.philosophy.entity.PhilosopherContent;

/**
 * 철학자 한 명의 글. 화면의 탭 하나
 *
 * @param name     철학자 이름 (탭 이름)
 * @param sections 소제목 + 내용. 위에서부터 차례로
 */
public record PhilosopherResponse(String name, List<SectionResponse> sections) {

    public record SectionResponse(String subtitle, String body) {
    }

    public static PhilosopherResponse from(PhilosopherContent content) {
        return new PhilosopherResponse(content.getPhilosopherName(), content.getSections().stream()
                .map(section -> new SectionResponse(section.subtitle(), section.body()))
                .toList());
    }
}
