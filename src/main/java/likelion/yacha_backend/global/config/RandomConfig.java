package likelion.yacha_backend.global.config;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 랜덤 뽑기에 쓰는 난수 생성기. 빈으로 두어 테스트에서 결과를 고정할 수 있게 합니다.
 *
 * <p>{@link SecureRandom} 은 여러 스레드가 같이 써도 안전하고, 예측하기 어려워 초대 코드처럼
 * 맞히면 안 되는 값에도 그대로 쓸 수 있습니다.
 */
@Configuration
public class RandomConfig {

    @Bean
    public RandomGenerator randomGenerator() {
        return new SecureRandom();
    }
}
