package likelion.yacha_backend.domain.philosophy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * 철학자 글의 한 묶음 (소제목 + 내용). 화면에서 위에서부터 차례로 보여줌
 */
@Embeddable
public record PhilosophySection(
        @Column(nullable = false, length = PhilosophySection.SUBTITLE_MAX_LENGTH)
        String subtitle,

        @Column(nullable = false, columnDefinition = "TEXT")
        String body
) {

    /** 소제목 글자 수 상한. 컬럼 길이와 같음 */
    public static final int SUBTITLE_MAX_LENGTH = 100;

    public PhilosophySection {
        Objects.requireNonNull(subtitle, "subtitle");
        Objects.requireNonNull(body, "body");
    }
}
