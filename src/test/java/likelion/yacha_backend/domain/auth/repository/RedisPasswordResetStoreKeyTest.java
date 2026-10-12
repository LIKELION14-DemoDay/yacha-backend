package likelion.yacha_backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.cluster.SlotHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 인증번호 확인 스크립트가 함께 다루는 세 키가 Redis 클러스터에서 같은 슬롯에 놓이는지
 *
 * Redis 없이 확인함. 슬롯은 키 문자열만으로 정해지고, 계산은 Redis 클라이언트(Lettuce)의 것을 그대로 씀
 */
@DisplayName("비밀번호 재설정 Redis 키 — 해시 태그")
class RedisPasswordResetStoreKeyTest {

    private static final String EMAIL = "soomin@example.com";

    @Test
    @DisplayName("인증번호 · 이메일 전체 실패 · IP별 실패 키가 같은 슬롯에 놓인다")
    void keysShareSlot() {
        int slot = SlotHash.getSlot(RedisPasswordResetStore.codeKey(EMAIL));

        assertThat(SlotHash.getSlot(RedisPasswordResetStore.failKey(EMAIL))).isEqualTo(slot);
        assertThat(SlotHash.getSlot(RedisPasswordResetStore.ipFailKey(EMAIL, "203.0.113.7"))).isEqualTo(slot);
        assertThat(SlotHash.getSlot(RedisPasswordResetStore.ipFailKey(EMAIL, "2001:db8::1"))).isEqualTo(slot);
    }

    @Test
    @DisplayName("슬롯은 이메일만으로 정해진다 (해시 태그가 이메일을 감쌈)")
    void slotIsDecidedByEmail() {
        assertThat(RedisPasswordResetStore.codeKey(EMAIL)).isEqualTo("password-reset-code:{soomin@example.com}");
        assertThat(RedisPasswordResetStore.failKey(EMAIL)).isEqualTo("password-reset-fail:{soomin@example.com}");
        assertThat(RedisPasswordResetStore.ipFailKey(EMAIL, "203.0.113.7"))
                .isEqualTo("password-reset-fail-ip:{soomin@example.com}:203.0.113.7");
        assertThat(SlotHash.getSlot(RedisPasswordResetStore.codeKey(EMAIL))).isEqualTo(SlotHash.getSlot(EMAIL));
    }
}
