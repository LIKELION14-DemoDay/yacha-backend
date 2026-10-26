package likelion.yacha_backend.domain.ai;

import java.util.List;

/**
 * 1분 철학 철학자 글. 매주 다음 주 주제로 한 번 만들고, 팀원이 검토 · 승인한 뒤 공개함
 *
 * 주제에 실제로 입장을 밝힌 철학자를 {@value #MIN_PHILOSOPHERS}~{@value #MAX_PHILOSOPHERS}명 골라
 * 철학자마다 {@value #TARGET_MIN_LENGTH}~{@value #TARGET_MAX_LENGTH}자 글을 "소제목 + 내용" 묶음으로 만듦
 *
 * 실패하면 예외를 던짐. 개수 · 길이 · 형식 검증과 재시도는 부르는 쪽이 함
 */
public interface PhilosophyWriter {

    int MIN_PHILOSOPHERS = 2;

    int MAX_PHILOSOPHERS = 3;

    /** 철학자 한 명의 글 길이 목표 (내용만, 공백 포함) */
    int TARGET_MIN_LENGTH = 300;

    int TARGET_MAX_LENGTH = 500;

    List<PhilosopherWriting> write(AiTopic topic);
}
