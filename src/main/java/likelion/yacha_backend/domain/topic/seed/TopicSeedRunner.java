package likelion.yacha_backend.domain.topic.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 서버가 뜰 때마다 DB 의 주제를 주제 파일({@code topics/topics.json})과 맞춥니다 (#54).
 *
 * <p>빈을 모두 만든 직후, 웹 서버가 요청을 받기 시작하기 전에 돕니다. {@code ApplicationRunner} 는 웹 서버가 이미
 * 요청을 받는 중에 돌아, 빈 DB 에 처음 넣는 사이 방을 만들면 {@code TOPIC_NOT_FOUND} 가 날 수 있어서입니다.
 * 파일이 잘못됐거나 DB 에 넣지 못하면 서버가 뜨지 않습니다. 주제 없이 뜨면 방 생성 · 자동 제안 · 봇전이 모두
 * 막히기 때문입니다. 테스트에서는 {@code topic.seed.enabled=false} 로 끕니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "topic.seed.enabled", havingValue = "true")
public class TopicSeedRunner implements SmartInitializingSingleton {

    private final TopicSeedService topicSeedService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterSingletonsInstantiated() {
        List<TopicSeed> seeds;
        try (InputStream in = new ClassPathResource(TopicSeedFile.LOCATION).getInputStream()) {
            seeds = TopicSeedFile.read(in, objectMapper);
        } catch (IOException e) {
            throw new UncheckedIOException("주제 파일을 열지 못했습니다: " + TopicSeedFile.LOCATION, e);
        }
        TopicSeedService.Result result = topicSeedService.sync(seeds);
        log.info("[주제] 주제 파일 {}건과 맞췄습니다. 추가 {}건, 수정 {}건, 내림 {}건",
                seeds.size(), result.created(), result.updated(), result.deactivated());
    }
}
