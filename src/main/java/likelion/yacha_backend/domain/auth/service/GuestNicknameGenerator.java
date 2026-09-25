package likelion.yacha_backend.domain.auth.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * 게스트 닉네임을 자동으로 만듦 예: {@code 성난 야차37}
 * 중복을 허용
 * 승격하면 사용자가 직접 정한 닉네임으로
 */
@Component
public class GuestNicknameGenerator {

    private static final List<String> ADJECTIVES = List.of(
            "성난", "조용한", "날카로운", "느긋한", "사려깊은", "뜨거운", "차분한",
            "고집센", "재빠른", "묵직한", "엉뚱한", "당당한");

    private static final int NUMBER_BOUND = 100;

    public String generate() {
        String adjective = ADJECTIVES.get(ThreadLocalRandom.current().nextInt(ADJECTIVES.size()));
        int number = ThreadLocalRandom.current().nextInt(NUMBER_BOUND);
        return "%s 야차%02d".formatted(adjective, number);
    }
}
