package likelion.yacha_backend.domain.topic.seed;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 서버가 뜰 때마다 DB 의 주제를 주제 파일({@code topics/topics.json})과 맞춥니다 (#54).
 *
 * <p>요청을 받기 전에 돌고, 파일이 잘못됐거나 DB 에 넣지 못하면 서버가 뜨지 않습니다. 주제 없이 뜨면
 * 방 생성 · 자동 제안 · 봇전이 모두 막히기 때문입니다. 테스트에서는 {@code topic.seed.enabled=false} 로 끕니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "topic.seed.enabled", havingValue = "true")
public class TopicSeedRunner implements ApplicationRunner {

    private final TopicSeedService topicSeedService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<TopicSeed> seeds;
        try (InputStream in = new ClassPathResource(TopicSeedFile.LOCATION).getInputStream()) {
            seeds = TopicSeedFile.read(in, objectMapper);
        }
        TopicSeedService.Result result = topicSeedService.sync(seeds);
        log.info("[주제] 주제 파일 {}건과 맞췄습니다. 추가 {}건, 수정 {}건, 내림 {}건",
                seeds.size(), result.created(), result.updated(), result.deactivated());
    }
}
