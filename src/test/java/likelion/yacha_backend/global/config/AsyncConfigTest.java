package likelion.yacha_backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

@DisplayName("메일 실행기 MDC 전달")
class AsyncConfigTest {

    private final TaskDecorator decorator = AsyncConfig.copyMdc();

    /** 스레드 하나짜리 풀. 같은 스레드를 다시 쓸 때 이전 값이 남는지 확인합니다. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        MDC.clear();
        worker.shutdownNow();
    }

    @Test
    @DisplayName("요청 스레드의 traceId 가 작업 스레드에서도 보인다")
    void copiesTraceId() throws Exception {
        MDC.put("traceId", "abc123");
        CompletableFuture<String> seen = new CompletableFuture<>();

        worker.execute(decorator.decorate(() -> seen.complete(MDC.get("traceId"))));

        assertThat(seen.get(5, TimeUnit.SECONDS)).isEqualTo("abc123");
    }

    @Test
    @DisplayName("작업이 끝나면 비워서, 재사용된 스레드에 이전 요청의 traceId 가 남지 않는다")
    void clearsAfterTask() throws Exception {
        MDC.put("traceId", "abc123");
        worker.execute(decorator.decorate(() -> { }));
        MDC.clear();

        // MDC 가 없는 쪽에서 넘긴 작업. 같은 스레드에서 돕니다.
        CompletableFuture<String> seen = new CompletableFuture<>();
        worker.execute(decorator.decorate(() -> seen.complete(MDC.get("traceId"))));

        assertThat(seen.get(5, TimeUnit.SECONDS)).isNull();
    }
}
