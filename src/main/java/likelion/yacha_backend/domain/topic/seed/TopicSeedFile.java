package likelion.yacha_backend.domain.topic.seed;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import likelion.yacha_backend.domain.topic.entity.Topic;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 주제 파일({@code topics/topics.json})을 읽고 검사합니다.
 *
 * <p>잘못된 주제가 하나라도 있으면 전부 모아 예외로 알립니다. 서버가 뜰 때 실패하므로, 배포 전에
 * {@code TopicSeedFileTest} 가 실제 파일을 읽어 CI 에서 먼저 걸러냅니다.
 */
public final class TopicSeedFile {

    public static final String LOCATION = "topics/topics.json";

    private TopicSeedFile() {
    }

    public static List<TopicSeed> read(InputStream in, ObjectMapper objectMapper) throws IOException {
        List<TopicSeed> seeds;
        try {
            seeds = objectMapper.readValue(in, new TypeReference<List<TopicSeed>>() {
            });
        } catch (JacksonException e) {
            // 없는 하위 카테고리 값도 여기서 걸립니다.
            throw new IllegalStateException("주제 파일을 읽지 못했습니다: " + e.getOriginalMessage(), e);
        }
        validate(seeds);
        return seeds;
    }

    static void validate(List<TopicSeed> seeds) {
        List<String> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        Set<String> statements = new HashSet<>();
        for (int i = 0; i < seeds.size(); i++) {
            TopicSeed seed = seeds.get(i);
            String where = (i + 1) + "번째 주제(" + (seed == null ? null : seed.code()) + ")";
            if (seed == null) {
                errors.add(where + ": 비어 있습니다");
                continue;
            }
            if (isBlank(seed.code())) {
                errors.add(where + ": code 가 없습니다");
            } else if (length(seed.code()) > Topic.CODE_MAX_LENGTH) {
                errors.add(where + ": code 가 " + Topic.CODE_MAX_LENGTH + "자를 넘습니다");
            } else if (!codes.add(seed.code())) {
                errors.add(where + ": code 가 다른 주제와 겹칩니다");
            }
            if (seed.subcategory() == null) {
                errors.add(where + ": subcategory 가 없습니다");
            }
            if (isBlank(seed.statement())) {
                errors.add(where + ": statement 가 없습니다");
            } else if (!statements.add(seed.statement().strip())) {
                errors.add(where + ": statement 가 다른 주제와 겹칩니다");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("주제 파일이 잘못됐습니다.\n" + String.join("\n", errors));
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** 이모지도 한 글자로 셉니다 (MySQL VARCHAR 길이와 같은 기준). */
    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
