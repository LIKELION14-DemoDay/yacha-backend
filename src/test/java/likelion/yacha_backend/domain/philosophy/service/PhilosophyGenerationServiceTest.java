package likelion.yacha_backend.domain.philosophy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.random.RandomGenerator;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.PhilosopherWriting;
import likelion.yacha_backend.domain.ai.PhilosopherWriting.Section;
import likelion.yacha_backend.domain.ai.PhilosophyWriter;
import likelion.yacha_backend.domain.philosophy.entity.PhilosopherContent;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophySection;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyStatus;
import likelion.yacha_backend.domain.philosophy.exception.PhilosophyErrorCode;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyArticleRepository;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.exception.TopicErrorCode;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.global.config.JpaAuditingConfig;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("PhilosophyGenerationService")
class PhilosophyGenerationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 목요일 */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate LAST_MONDAY = NEXT_MONDAY.minusWeeks(1);
    private static final String BODY = "가".repeat(300);

    @Autowired
    private PhilosophyArticleRepository articleRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 정해 둔 결과를 차례로 돌려주고(다 쓰면 올바른 글), 불린 횟수와 받은 주제를 기록 */
    static class ScriptedWriter implements PhilosophyWriter {
        private final Deque<Supplier<List<PhilosopherWriting>>> script = new ArrayDeque<>();
        int calls;
        AiTopic lastTopic;

        ScriptedWriter then(Supplier<List<PhilosopherWriting>> step) {
            script.add(step);
            return this;
        }

        ScriptedWriter thenFail(int times) {
            for (int i = 0; i < times; i++) {
                then(() -> {
                    throw new IllegalStateException("network");
                });
            }
            return this;
        }

        @Override
        public List<PhilosopherWriting> write(AiTopic topic) {
            calls++;
            lastTopic = topic;
            return script.isEmpty() ? valid() : script.poll().get();
        }
    }

    /** 늘 남은 후보 중 첫째를 고름 (id 오름차순) */
    static class FirstPick implements RandomGenerator {
        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }

    private static List<PhilosopherWriting> valid() {
        return List.of(
                new PhilosopherWriting("아리스토텔레스", List.of(new Section("소제목", BODY))),
                new PhilosopherWriting("플라톤", List.of(new Section("소제목", BODY))));
    }

    private PhilosophyGenerationService service(PhilosophyWriter writer, int topicsPerWeek, LocalDate today) {
        Clock clock = Clock.fixed(today.atStartOfDay(KST).toInstant(), KST);
        return new PhilosophyGenerationService(articleRepository, topicRepository, writer, transactionManager,
                new FirstPick(), new PhilosophyGenerationProperties(topicsPerWeek, 3, Duration.ZERO), clock);
    }

    private PhilosophyGenerationService service(PhilosophyWriter writer, int topicsPerWeek) {
        return service(writer, topicsPerWeek, TODAY);
    }

    private Topic topic(String statement) {
        return topicRepository.save(Topic.create(Subcategory.LYING, statement, "찬성", "반대"));
    }

    private List<Topic> topics(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> topic("주제 " + i)).toList();
    }

    private void saveArticle(Topic topic, LocalDate weekStart) {
        articleRepository.save(PhilosophyArticle.createPending(topic, weekStart));
    }

    private List<Long> topicIdsOf(List<Long> articleIds) {
        return articleIds.stream()
                .map(id -> articleRepository.findById(id).orElseThrow().getTopic().getId())
                .toList();
    }

    @Nested
    @DisplayName("다음 주 시작일")
    class NextWeekStart {

        @Test
        @DisplayName("목요일이면 다음 월요일")
        void fromThursday() {
            assertThat(service(new ScriptedWriter(), 5).nextWeekStart()).isEqualTo(NEXT_MONDAY);
        }

        @Test
        @DisplayName("월요일이면 일주일 뒤 월요일")
        void fromMonday() {
            assertThat(service(new ScriptedWriter(), 5, LocalDate.of(2026, 10, 26)).nextWeekStart())
                    .isEqualTo(NEXT_MONDAY);
        }
    }

    @Nested
    @DisplayName("편수")
    class Count {

        @Test
        @DisplayName("정해진 편수만큼 서로 다른 주제로 만들어 검토 전으로 저장한다")
        void generatesTopicsPerWeek() {
            List<Topic> all = topics(6);
            ScriptedWriter writer = new ScriptedWriter();

            PhilosophyGenerationResult result = service(writer, 5).generate(NEXT_MONDAY);

            assertThat(result.createdIds()).hasSize(5);
            assertThat(result.failed()).isZero();
            assertThat(writer.calls).isEqualTo(5);
            assertThat(topicIdsOf(result.createdIds())).doesNotHaveDuplicates()
                    .containsExactlyElementsOf(all.subList(0, 5).stream().map(Topic::getId).toList());
            assertThat(articleRepository.findAllById(result.createdIds()))
                    .allSatisfy(article -> {
                        assertThat(article.getStatus()).isEqualTo(PhilosophyStatus.PENDING);
                        assertThat(article.getWeekStart()).isEqualTo(NEXT_MONDAY);
                    });
        }

        @Test
        @DisplayName("이미 있는 편은 빼고 모자란 만큼만 만든다")
        void fillsMissingOnly() {
            List<Topic> all = topics(5);
            saveArticle(all.get(0), NEXT_MONDAY);
            saveArticle(all.get(1), NEXT_MONDAY);
            ScriptedWriter writer = new ScriptedWriter();

            PhilosophyGenerationResult result = service(writer, 5).generate(NEXT_MONDAY);

            assertThat(result.createdIds()).hasSize(3);
            assertThat(writer.calls).isEqualTo(3);
            assertThat(articleRepository.countByWeekStart(NEXT_MONDAY)).isEqualTo(5);
        }

        @Test
        @DisplayName("이미 다 있으면 WEEK_ALREADY_FULL이고 AI를 부르지 않는다")
        void alreadyFull() {
            List<Topic> all = topics(2);
            saveArticle(all.get(0), NEXT_MONDAY);
            saveArticle(all.get(1), NEXT_MONDAY);
            ScriptedWriter writer = new ScriptedWriter();

            assertErrorCode(() -> service(writer, 2).generate(NEXT_MONDAY), PhilosophyErrorCode.WEEK_ALREADY_FULL);
            assertThat(writer.calls).isZero();
        }
    }

    @Test
    @DisplayName("AI가 만든 철학자 글을 순서대로 저장하고, 주제 문장을 AI에 넘긴다")
    void savesWritings() {
        topic("행복을 위해 거짓말을 해도 될까?");
        ScriptedWriter writer = new ScriptedWriter();

        Long id = service(writer, 1).generate(NEXT_MONDAY).createdIds().get(0);

        PhilosophyArticle saved = articleRepository.findById(id).orElseThrow();
        assertThat(saved.getContents()).extracting(PhilosopherContent::getPhilosopherName)
                .containsExactly("아리스토텔레스", "플라톤");
        assertThat(saved.getContents().get(0).getSections())
                .containsExactly(new PhilosophySection("소제목", BODY));
        assertThat(writer.lastTopic.statement()).isEqualTo("행복을 위해 거짓말을 해도 될까?");
    }

    @Nested
    @DisplayName("주제 고르기")
    class PickTopics {

        @Test
        @DisplayName("이미 1분 철학에 쓴 주제보다 안 쓴 주제를 먼저 고른다")
        void prefersUnused() {
            List<Topic> all = topics(3);
            saveArticle(all.get(0), LAST_MONDAY);

            PhilosophyGenerationResult result = service(new ScriptedWriter(), 2).generate(NEXT_MONDAY);

            assertThat(topicIdsOf(result.createdIds())).containsExactly(all.get(1).getId(), all.get(2).getId());
        }

        @Test
        @DisplayName("안 쓴 주제가 모자라면 이미 쓴 주제에서 채운다")
        void reusesWhenUnusedShort() {
            List<Topic> all = topics(2);
            saveArticle(all.get(0), LAST_MONDAY);

            PhilosophyGenerationResult result = service(new ScriptedWriter(), 2).generate(NEXT_MONDAY);

            assertThat(topicIdsOf(result.createdIds())).containsExactly(all.get(1).getId(), all.get(0).getId());
        }

        @Test
        @DisplayName("그 주에 이미 있는 주제는 다시 고르지 않고, 고를 주제가 모자라면 그만큼 실패로 센다")
        void excludesTopicsInSameWeek() {
            List<Topic> all = topics(2);
            saveArticle(all.get(0), NEXT_MONDAY);
            saveArticle(all.get(1), LAST_MONDAY);

            PhilosophyGenerationResult result = service(new ScriptedWriter(), 3).generate(NEXT_MONDAY);

            assertThat(topicIdsOf(result.createdIds())).containsExactly(all.get(1).getId());
            assertThat(result.failed()).isEqualTo(1);
        }

        @Test
        @DisplayName("내린 주제는 고르지 않는다")
        void skipsInactive() {
            topic("내린 주제").deactivate();
            Topic active = topic("활성 주제");

            PhilosophyGenerationResult result = service(new ScriptedWriter(), 2).generate(NEXT_MONDAY);

            assertThat(topicIdsOf(result.createdIds())).containsExactly(active.getId());
            assertThat(result.failed()).isEqualTo(1);
        }

        @Test
        @DisplayName("고를 주제가 하나도 없으면 TOPIC_NOT_FOUND이고 AI를 부르지 않는다")
        void noTopic() {
            ScriptedWriter writer = new ScriptedWriter();

            assertErrorCode(() -> service(writer, 5).generate(NEXT_MONDAY), TopicErrorCode.TOPIC_NOT_FOUND);
            assertThat(writer.calls).isZero();
        }
    }

    @Test
    @DisplayName("월요일이 아니면 INVALID_WEEK_START")
    void notMonday() {
        topic("주제");

        assertErrorCode(() -> service(new ScriptedWriter(), 5).generate(NEXT_MONDAY.plusDays(1)),
                PhilosophyErrorCode.INVALID_WEEK_START);
    }

    @Nested
    @DisplayName("실패 · 재시도")
    class Failure {

        @Test
        @DisplayName("AI 실패와 형식이 맞지 않는 응답은 다시 시도한다")
        void retriesFailureAndInvalidOutput() {
            topic("주제");
            ScriptedWriter writer = new ScriptedWriter()
                    .then(() -> {
                        throw new AiException("timeout", true);
                    })
                    .then(() -> List.of(new PhilosopherWriting("플라톤", List.of(new Section("소제목", BODY)))))
                    .then(PhilosophyGenerationServiceTest::valid);

            PhilosophyGenerationResult result = service(writer, 1).generate(NEXT_MONDAY);

            assertThat(writer.calls).isEqualTo(3);
            assertThat(result.createdIds()).hasSize(1);
        }

        @Test
        @DisplayName("한 편이 끝내 실패해도 원인을 로그로 남기고 나머지 편은 만든다")
        void continuesAfterFailure(CapturedOutput output) {
            List<Topic> all = topics(3);
            ScriptedWriter writer = new ScriptedWriter().thenFail(3);

            PhilosophyGenerationResult result = service(writer, 3).generate(NEXT_MONDAY);

            assertThat(result.failed()).isEqualTo(1);
            assertThat(topicIdsOf(result.createdIds())).containsExactly(all.get(1).getId(), all.get(2).getId());
            assertThat(writer.calls).isEqualTo(5);
            assertThat(output).contains("글을 만들지 못해 다음 주제로 넘어갑니다. weekStart=2026-11-02, topicId=" + all.get(0).getId())
                    .contains("IllegalStateException: network");
        }

        @Test
        @DisplayName("재시도 대기 중 중단되면(서버 종료) 남은 편은 AI를 부르지 않고 모두 실패로 센다")
        void stopsWhenInterrupted(CapturedOutput output) {
            topics(3);
            ScriptedWriter writer = new ScriptedWriter().then(() -> {
                // 첫 시도가 실패한 뒤 재시도 대기에서 중단되게 함
                Thread.currentThread().interrupt();
                throw new IllegalStateException("network");
            });

            PhilosophyGenerationResult result;
            try {
                result = service(writer, 3).generate(NEXT_MONDAY);
            } finally {
                // 중단 표시가 남으면 테스트 정리(롤백)가 실패함
                Thread.interrupted();
            }

            assertThat(writer.calls).isEqualTo(1);
            assertThat(result.createdIds()).isEmpty();
            assertThat(result.failed()).isEqualTo(3);
            assertThat(output).contains("생성이 중단돼 남은 2편은 만들지 않았습니다");
        }

        @Test
        @DisplayName("다시 해도 안 되는 실패면 남은 편도 멈추고 모두 실패로 센다")
        void stopsOnNonRetryable() {
            topics(3);
            ScriptedWriter writer = new ScriptedWriter().then(() -> {
                throw new AiException("bad key", false);
            });

            PhilosophyGenerationResult result = service(writer, 3).generate(NEXT_MONDAY);

            assertThat(writer.calls).isEqualTo(1);
            assertThat(result.createdIds()).isEmpty();
            assertThat(result.failed()).isEqualTo(3);
            assertThat(articleRepository.countByWeekStart(NEXT_MONDAY)).isZero();
        }
    }

    @Nested
    @DisplayName("다시 생성")
    class Regenerate {

        private PhilosophyArticle approvedArticle() {
            PhilosophyArticle article = PhilosophyArticle.createPending(topic("주제"), NEXT_MONDAY);
            article.addContent("니체", List.of(new PhilosophySection("옛 소제목", "옛 내용")));
            article.approve(TODAY.atStartOfDay());
            return articleRepository.saveAndFlush(article);
        }

        @Test
        @DisplayName("같은 주제로 글을 바꾸고 검토 전으로 되돌린다")
        void replaces() {
            PhilosophyArticle article = approvedArticle();
            ScriptedWriter writer = new ScriptedWriter();

            service(writer, 5).regenerate(article.getId());

            PhilosophyArticle found = articleRepository.findById(article.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(PhilosophyStatus.PENDING);
            assertThat(found.getContents()).extracting(PhilosopherContent::getPhilosopherName)
                    .containsExactly("아리스토텔레스", "플라톤");
            assertThat(writer.lastTopic.statement()).isEqualTo("주제");
        }

        @Test
        @DisplayName("AI가 끝내 실패하면 이전 글과 승인이 그대로 남는다")
        void keepsOldOnFailure() {
            PhilosophyArticle article = approvedArticle();
            ScriptedWriter writer = new ScriptedWriter().thenFail(3);

            assertThatThrownBy(() -> service(writer, 5).regenerate(article.getId()))
                    .isInstanceOf(IllegalStateException.class);

            PhilosophyArticle found = articleRepository.findById(article.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(PhilosophyStatus.APPROVED);
            assertThat(found.getContents()).extracting(PhilosopherContent::getPhilosopherName)
                    .containsExactly("니체");
        }

        @Test
        @DisplayName("공개된 글이면 ARTICLE_ALREADY_PUBLISHED이고 AI를 부르지 않는다")
        void publishedIsLocked() {
            PhilosophyArticle article = approvedArticle();
            ScriptedWriter writer = new ScriptedWriter();

            assertErrorCode(() -> service(writer, 5, NEXT_MONDAY).regenerate(article.getId()),
                    PhilosophyErrorCode.ARTICLE_ALREADY_PUBLISHED);
            assertThat(writer.calls).isZero();
        }

        @Test
        @DisplayName("없는 글이면 ARTICLE_NOT_FOUND")
        void notFound() {
            assertErrorCode(() -> service(new ScriptedWriter(), 5).regenerate(999_999L),
                    PhilosophyErrorCode.ARTICLE_NOT_FOUND);
        }
    }

    private static void assertErrorCode(Runnable call, Object errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
