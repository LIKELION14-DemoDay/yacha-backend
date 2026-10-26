package likelion.yacha_backend.domain.philosophy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.ai.PhilosopherWriting;
import likelion.yacha_backend.domain.ai.PhilosopherWriting.Section;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PhilosophyWritingValidator")
class PhilosophyWritingValidatorTest {

    private static final String BODY_300 = "가".repeat(300);

    @Test
    @DisplayName("이름 · 소제목 · 내용의 앞뒤 공백을 정리해 돌려준다")
    void stripsWhitespace() {
        List<PhilosopherWriting> result = PhilosophyWritingValidator.validate(List.of(
                writing("  플라톤 ", new Section(" 소제목 ", " " + BODY_300 + "\n")),
                writing("칸트", new Section("소제목", BODY_300))));

        assertThat(result.get(0)).isEqualTo(writing("플라톤", new Section("소제목", BODY_300)));
    }

    @Test
    @DisplayName("철학자는 2~3명이어야 한다")
    void philosopherCount() {
        assertInvalid(List.of(writing("플라톤", new Section("소제목", BODY_300))));
        assertInvalid(List.of(
                writing("플라톤", new Section("소제목", BODY_300)),
                writing("칸트", new Section("소제목", BODY_300)),
                writing("니체", new Section("소제목", BODY_300)),
                writing("밀", new Section("소제목", BODY_300))));
        assertInvalid(null);
    }

    @Test
    @DisplayName("같은 철학자가 두 번 나오면 안 된다")
    void duplicateName() {
        assertInvalid(List.of(
                writing("플라톤", new Section("소제목", BODY_300)),
                writing(" 플라톤", new Section("소제목", BODY_300))));
    }

    @Test
    @DisplayName("이름 · 소제목 · 내용이 비면 안 된다")
    void blankFields() {
        assertInvalid(withFirst(writing(" ", new Section("소제목", BODY_300))));
        assertInvalid(withFirst(writing("플라톤", new Section(" ", BODY_300))));
        assertInvalid(withFirst(writing("플라톤",
                new Section("소제목", BODY_300), new Section("소제목", " "))));
        assertInvalid(withFirst(new PhilosopherWriting("플라톤", List.of())));
    }

    @Test
    @DisplayName("이름 50자 · 소제목 100자를 넘으면 안 된다 (컬럼 길이)")
    void columnLengths() {
        assertInvalid(withFirst(writing("가".repeat(51), new Section("소제목", BODY_300))));
        assertInvalid(withFirst(writing("플라톤", new Section("가".repeat(101), BODY_300))));
    }

    @Test
    @DisplayName("소제목 + 내용 묶음은 5개까지다")
    void maxSections() {
        Section section = new Section("소제목", "가".repeat(60));

        assertInvalid(withFirst(writing("플라톤", section, section, section, section, section, section)));
    }

    @Test
    @DisplayName("철학자 한 명의 내용은 합쳐서 250 ~ 600자다")
    void bodyLength() {
        PhilosophyWritingValidator.validate(withFirst(writing("플라톤",
                new Section("소제목", "가".repeat(125)), new Section("소제목", "가".repeat(125)))));
        PhilosophyWritingValidator.validate(withFirst(writing("플라톤", new Section("소제목", "가".repeat(600)))));

        assertInvalid(withFirst(writing("플라톤", new Section("소제목", "가".repeat(249)))));
        assertInvalid(withFirst(writing("플라톤", new Section("소제목", "가".repeat(601)))));
    }

    @Test
    @DisplayName("형식이 맞지 않으면 다시 시도할 수 있는 실패로 알린다")
    void invalidIsRetryable() {
        assertThatThrownBy(() -> PhilosophyWritingValidator.validate(List.of()))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.isRetryable()).isTrue());
    }

    private static PhilosopherWriting writing(String name, Section... sections) {
        return new PhilosopherWriting(name, List.of(sections));
    }

    /** 검사할 글을 첫째로 두고, 둘째는 올바른 글로 채움 */
    private static List<PhilosopherWriting> withFirst(PhilosopherWriting first) {
        return List.of(first, writing("칸트", new Section("소제목", BODY_300)));
    }

    private static void assertInvalid(List<PhilosopherWriting> writings) {
        assertThatThrownBy(() -> PhilosophyWritingValidator.validate(writings)).isInstanceOf(AiException.class);
    }
}
