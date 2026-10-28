package likelion.yacha_backend.domain.topic.seed;

import likelion.yacha_backend.domain.topic.entity.Subcategory;

/**
 * 주제 파일({@code topics/topics.json})의 주제 한 건. 답은 예 / 아니오로만 받아 입장 문구는 적지 않습니다.
 *
 * @param code         주제 키. 파일 안에서 겹치면 안 되고, 한 번 정하면 바꾸지 않습니다 (바꾸면 다른 주제로 봅니다)
 * @param subcategory  하위 카테고리. 상위 카테고리는 이 값으로 정해집니다
 * @param statement    주제 화면 · 대기 목록에 뜨는 질문. 예 / 아니오로 답할 수 있어야 합니다. 앞뒤 공백은 지웁니다
 * @param active       랜덤 추첨 대상 여부. 빼면 true
 */
public record TopicSeed(
        String code,
        Subcategory subcategory,
        String statement,
        Boolean active
) {

    public TopicSeed {
        statement = statement == null ? null : statement.strip();
    }

    public boolean isActive() {
        return active == null || active;
    }
}
