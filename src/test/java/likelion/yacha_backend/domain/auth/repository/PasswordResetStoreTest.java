package likelion.yacha_backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import likelion.yacha_backend.domain.auth.repository.PasswordResetStore.CodeCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("비밀번호 재설정 저장소")
class PasswordResetStoreTest {

    @Autowired
    private PasswordResetStore store;

    @Test
    @DisplayName("테스트에서는 Redis 대신 인메모리 구현이 주입된다")
    void usesInMemoryInTests() {
        assertThat(store).isInstanceOf(InMemoryPasswordResetStore.class);
    }

    @Test
    @DisplayName("저장한 토큰으로 userId 를 꺼낸다")
    void saveAndConsume() {
        store.save("token-1", 7L);

        assertThat(store.consume("token-1")).contains(7L);
    }

    @Test
    @DisplayName("같은 토큰은 두 번 쓸 수 없다 (1회용)")
    void consumeOnlyOnce() {
        store.save("token-2", 7L);

        assertThat(store.consume("token-2")).contains(7L);
        assertThat(store.consume("token-2")).isEmpty();
    }

    @Test
    @DisplayName("없는 토큰은 빈 값이다")
    void unknownToken() {
        assertThat(store.consume("no-such-token")).isEmpty();
    }

    @Test
    @DisplayName("같은 이메일로 연속 요청하면 두 번째는 막힌다")
    void sendSlotIsLimited() {
        assertThat(store.tryAcquireSendSlot("a@example.com")).isTrue();
        assertThat(store.tryAcquireSendSlot("a@example.com")).isFalse();
    }

    @Test
    @DisplayName("다른 이메일은 서로 영향을 주지 않는다")
    void sendSlotIsPerEmail() {
        assertThat(store.tryAcquireSendSlot("b@example.com")).isTrue();
        assertThat(store.tryAcquireSendSlot("c@example.com")).isTrue();
    }

    @Test
    @DisplayName("같은 사용자에게 새 토큰을 발급하면 이전 토큰은 쓸 수 없다")
    void newTokenInvalidatesPrevious() {
        store.save("token-a", 101L);
        store.save("token-b", 101L);

        assertThat(store.consume("token-a")).isEmpty();
        assertThat(store.consume("token-b")).contains(101L);
    }

    @Test
    @DisplayName("새 토큰으로 재설정한 뒤에도 이전 토큰은 쓸 수 없다")
    void previousTokenStaysInvalidAfterReset() {
        store.save("token-c", 102L);
        store.save("token-d", 102L);

        assertThat(store.consume("token-d")).contains(102L);
        assertThat(store.consume("token-c")).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 토큰은 서로 무효화하지 않는다")
    void tokensArePerUser() {
        store.save("token-e", 103L);
        store.save("token-f", 104L);

        assertThat(store.consume("token-e")).contains(103L);
        assertThat(store.consume("token-f")).contains(104L);
    }

    private static final String MY_IP = "198.51.100.2";
    private static final String OTHER_IP = "203.0.113.7";

    @Test
    @DisplayName("인증번호가 맞으면 MATCHED이고, 같은 번호로 다시 확인하면 EXPIRED (1회용)")
    void codeMatchesOnce() {
        store.saveCode("code1@example.com", "123456");

        assertThat(store.checkCode("code1@example.com", MY_IP, "123456")).isEqualTo(CodeCheck.MATCHED);
        assertThat(store.checkCode("code1@example.com", MY_IP, "123456")).isEqualTo(CodeCheck.EXPIRED);
    }

    @Test
    @DisplayName("틀리면 MISMATCHED이고, 남은 횟수 안에서는 맞는 번호로 통과한다")
    void mismatchThenMatch() {
        store.saveCode("code2@example.com", "123456");

        assertThat(store.checkCode("code2@example.com", MY_IP, "000000")).isEqualTo(CodeCheck.MISMATCHED);
        assertThat(store.checkCode("code2@example.com", MY_IP, "123456")).isEqualTo(CodeCheck.MATCHED);
    }

    @Test
    @DisplayName("5번 틀리면 맞는 번호도 EXPIRED")
    void expiresAfterMaxAttempts() {
        store.saveCode("code3@example.com", "123456");
        for (int i = 0; i < 5; i++) {
            assertThat(store.checkCode("code3@example.com", MY_IP, "000000")).isEqualTo(CodeCheck.MISMATCHED);
        }

        assertThat(store.checkCode("code3@example.com", MY_IP, "123456")).isEqualTo(CodeCheck.EXPIRED);
    }

    @Test
    @DisplayName("새로 저장하면 이전 인증번호는 틀린 번호가 되고 틀린 횟수도 처음부터 센다")
    void newCodeReplacesPrevious() {
        store.saveCode("code4@example.com", "111111");
        for (int i = 0; i < 4; i++) {
            store.checkCode("code4@example.com", MY_IP, "000000");
        }

        store.saveCode("code4@example.com", "222222");

        assertThat(store.checkCode("code4@example.com", MY_IP, "111111")).isEqualTo(CodeCheck.MISMATCHED);
        assertThat(store.checkCode("code4@example.com", MY_IP, "222222")).isEqualTo(CodeCheck.MATCHED);
    }

    @Test
    @DisplayName("요청한 적 없는 이메일은 EXPIRED")
    void unknownEmailIsExpired() {
        assertThat(store.checkCode("never@example.com", MY_IP, "123456")).isEqualTo(CodeCheck.EXPIRED);
    }

    /** 인증번호를 새로 받으며 틀리기를 반복해, 그 IP의 하루 실패 횟수를 count 만큼 채움 */
    private void failTimes(String email, String clientIp, int count) {
        for (int i = 0; i < count; i++) {
            if (i % 5 == 0) {
                store.saveCode(email, "123456");   // 5번마다 새로 받아 인증번호당 횟수를 되돌림
            }
            assertThat(store.checkCode(email, clientIp, "000000")).isEqualTo(CodeCheck.MISMATCHED);
        }
    }

    @Test
    @DisplayName("인증번호를 새로 받아도 같은 IP가 하루 10번 틀리면 맞는 번호도 LOCKED")
    void locksIpAfterDailyFailureLimit() {
        failTimes("lock1@example.com", OTHER_IP, 10);

        store.saveCode("lock1@example.com", "654321");
        assertThat(store.checkCode("lock1@example.com", OTHER_IP, "654321")).isEqualTo(CodeCheck.LOCKED);
    }

    @Test
    @DisplayName("남이 10번 틀려 잠겨도, 다른 IP(본인)는 맞는 번호로 통과한다")
    void otherIpIsNotLocked() {
        failTimes("lock2@example.com", OTHER_IP, 10);

        store.saveCode("lock2@example.com", "654321");
        assertThat(store.checkCode("lock2@example.com", OTHER_IP, "654321")).isEqualTo(CodeCheck.LOCKED);
        assertThat(store.checkCode("lock2@example.com", MY_IP, "654321")).isEqualTo(CodeCheck.MATCHED);
    }

    @Test
    @DisplayName("잠긴 IP는 인증번호의 시도 횟수를 쓰지 못한다 (본인의 번호를 계속 소진시킬 수 없음)")
    void lockedIpDoesNotBurnAttempts() {
        failTimes("lock3@example.com", OTHER_IP, 10);

        store.saveCode("lock3@example.com", "654321");
        for (int i = 0; i < 10; i++) {
            assertThat(store.checkCode("lock3@example.com", OTHER_IP, "000000")).isEqualTo(CodeCheck.LOCKED);
        }
        assertThat(store.checkCode("lock3@example.com", MY_IP, "654321")).isEqualTo(CodeCheck.MATCHED);
    }

    @Test
    @DisplayName("IP를 바꿔 가며 틀려도 이메일 전체로 100번이면 모든 IP가 LOCKED")
    void locksEmailAfterTotalFailureLimit() {
        // IP 10개가 9번씩 = 90번, 한 IP가 10번 = 합쳐서 100번. 어느 IP도 혼자서는 다른 IP를 잠그지 못함
        for (int ip = 0; ip < 10; ip++) {
            failTimes("lock4@example.com", "192.0.2." + ip, 9);
        }
        store.saveCode("lock4@example.com", "654321");
        assertThat(store.checkCode("lock4@example.com", MY_IP, "000000")).isEqualTo(CodeCheck.MISMATCHED);   // 91번째
        failTimes("lock4@example.com", "192.0.2.100", 9);                                                     // 100번째까지

        store.saveCode("lock4@example.com", "654321");
        assertThat(store.checkCode("lock4@example.com", MY_IP, "654321")).isEqualTo(CodeCheck.LOCKED);
        assertThat(store.checkCode("lock4@example.com", "192.0.2.200", "654321")).isEqualTo(CodeCheck.LOCKED);
    }

    @Test
    @DisplayName("9번 틀린 뒤에는 맞는 번호로 통과하고, 통과하면 틀린 횟수도 지운다")
    void matchClearsFailures() {
        failTimes("lock5@example.com", MY_IP, 9);

        store.saveCode("lock5@example.com", "654321");
        assertThat(store.checkCode("lock5@example.com", MY_IP, "654321")).isEqualTo(CodeCheck.MATCHED);

        // 지워졌으므로 다시 9번까지 틀려도 잠기지 않음
        failTimes("lock5@example.com", MY_IP, 9);
        store.saveCode("lock5@example.com", "111222");
        assertThat(store.checkCode("lock5@example.com", MY_IP, "111222")).isEqualTo(CodeCheck.MATCHED);
    }

    @Test
    @DisplayName("하루 실패 횟수는 이메일마다 따로 센다")
    void failuresArePerEmail() {
        failTimes("lock6@example.com", MY_IP, 10);

        store.saveCode("lock7@example.com", "123456");
        assertThat(store.checkCode("lock7@example.com", MY_IP, "123456")).isEqualTo(CodeCheck.MATCHED);
    }
}
