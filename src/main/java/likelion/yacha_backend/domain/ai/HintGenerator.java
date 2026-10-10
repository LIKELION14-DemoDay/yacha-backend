package likelion.yacha_backend.domain.ai;

import java.util.List;
import likelion.yacha_backend.domain.session.entity.Stance;

/**
 * 힌트 키워드 (명세 2-5). 주장 작성 화면의 "힌트 보기" 에 뜹니다.
 *
 * <p>매칭 직후 양쪽 것을 만듭니다. PREP 60초 안에 끝내는 것이 목표입니다.
 * 실패하면 예외를 던지세요. 재시도 · {@code FAILED} 처리는 부르는 쪽이 합니다.
 */
public interface HintGenerator {

    /** 힌트 개수 */
    int HINT_COUNT = 3;

    /** 힌트 한 개의 글자 수 상한 */
    int HINT_MAX_LENGTH = 20;

    /**
     * @param topic  주제
     * @param stance 힌트를 받을 참가자의 입장
     * @return 입장에 맞는 짧은 문장 {@value #HINT_COUNT}개, 각 {@value #HINT_MAX_LENGTH}자 이내
     */
    List<String> generate(AiTopic topic, Stance stance);
}
