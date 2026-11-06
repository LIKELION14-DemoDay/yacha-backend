package likelion.yacha_backend.domain.heart.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import likelion.yacha_backend.domain.heart.dto.HeartResponse;
import likelion.yacha_backend.domain.heart.entity.HeartUse;
import likelion.yacha_backend.domain.heart.entity.UserHeart;
import likelion.yacha_backend.domain.heart.exception.HeartErrorCode;
import likelion.yacha_backend.domain.heart.repository.HeartUseRepository;
import likelion.yacha_backend.domain.heart.repository.UserHeartRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.util.DateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 무료 하트(생명). 한 판에 1개를 쓰고, 하나 쓰면 충전 간격마다 1개씩 최대까지 다시 찹니다.
 *
 * <pre>
 * 방 만들기 · 입장 ─▶ checkAvailable : 0개면 HEART_EXHAUSTED (깎지 않음)
 * 게임 시작         ─▶ consume        : 매칭 트랜잭션 안에서 1개 깎고 사용 기록을 남김
 * 무효 · 판정 실패  ─▶ refund         : 사용 기록마다 한 번만 1개 돌려줌
 * </pre>
 *
 * <p>게임 시작은 서버의 세 곳뿐입니다 — 랜덤 방 입장 · 친구 방 입장(두 사람 모두), 봇전 전환(방장만).
 * 프론트가 부르는 차감 API 는 두지 않습니다.
 *
 * <p>같은 사람의 차감이 겹치는 일은 없습니다. 한 사람은 대기 · 진행 중인 세션에 하나만 들어가므로({@code ALREADY_IN_SESSION})
 * 게임 시작도 한 번에 하나뿐입니다. 판정 실패로 돌려주기는 다른 스레드에서 오므로 하트 행은 잠그고 고칩니다.
 *
 * <p><b>행이 없을 때는 잠그지 않고 바로 만듭니다.</b> 없는 행을 {@code SELECT ... FOR UPDATE} 하면 MySQL 이 그 주변 빈 구간을
 * 잠가, 처음 하트를 쓰는 두 사람이 동시에 행을 만들 때 서로의 INSERT 를 막아 교착이 납니다. 같은 사람의 행을 둘이 만드는 일은
 * 없으므로(위 이유) 있는지 먼저 보고 없으면 INSERT 합니다.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(HeartProperties.class)
@Transactional
public class HeartService {

    private final UserHeartRepository userHeartRepository;
    private final HeartUseRepository heartUseRepository;
    private final UserRepository userRepository;
    private final DebateSessionRepository sessionRepository;
    private final HeartProperties properties;
    private final EntityManager entityManager;
    private final Clock clock;

    /** 내 하트. 행이 없으면 가득 찬 것으로 봅니다. */
    @Transactional(readOnly = true)
    public HeartResponse getMyHearts(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return userHeartRepository.findById(userId)
                .map(heart -> new HeartResponse(
                        heart.current(now, properties.max(), properties.rechargeInterval()),
                        properties.max(),
                        DateTimes.withOffset(heart.nextChargeAt(now, properties.max(), properties.rechargeInterval()),
                                clock.getZone())))
                .orElseGet(() -> new HeartResponse(properties.max(), properties.max(), null));
    }

    /**
     * 하트가 1개 이상인지 확인합니다. 방을 만들거나 들어가기 전에 부르고, 깎지는 않습니다.
     *
     * @throws BusinessException {@code HEART_EXHAUSTED} 남은 하트가 0개
     */
    public void checkAvailable(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean exhausted = userHeartRepository.findById(userId)
                .map(heart -> heart.current(now, properties.max(), properties.rechargeInterval()) <= 0)
                .orElse(false);
        if (exhausted) {
            throw new BusinessException(HeartErrorCode.HEART_EXHAUSTED);
        }
    }

    /**
     * 게임 시작에 하트 1개를 쓰고 사용 기록을 남깁니다. 매칭 트랜잭션 안에서만 부릅니다 — 시작이 롤백되면 차감도 롤백됩니다.
     *
     * @throws BusinessException {@code HEART_EXHAUSTED} 남은 하트가 0개
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(Long sessionId, Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        UserHeart heart = userHeartRepository.existsById(userId) ? lockedHeart(userId) : null;
        if (heart == null) {
            heart = userHeartRepository.save(
                    UserHeart.full(userRepository.getReferenceById(userId), properties.max(), now));
        }
        if (!heart.consume(now, properties.max(), properties.rechargeInterval())) {
            throw new BusinessException(HeartErrorCode.HEART_EXHAUSTED);
        }
        heartUseRepository.save(HeartUse.of(
                sessionRepository.getReferenceById(sessionId), userRepository.getReferenceById(userId), now));
    }

    /**
     * 이 판에 쓴 하트를 돌려줍니다 — 게임이 무효(ABORTED)로 끝났거나 판정이 끝내 실패했을 때.
     * 사용 기록마다 한 번만 돌려주고, 최대를 넘지 않습니다. 사용 기록이 없는 사람(하트 기능 전에 시작한 판)은 건너뜁니다.
     * 무효 처리는 종료 트랜잭션 안에서 불러 종료와 함께 커밋되고, 판정 실패는 따로 트랜잭션을 엽니다.
     */
    public void refund(Long sessionId) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long userId : heartUseRepository.findUnrefundedUserIds(sessionId)) {
            if (heartUseRepository.markRefunded(sessionId, userId) == 0) {
                continue;
            }
            UserHeart heart = lockedHeart(userId);
            if (heart != null) {
                heart.refund(now, properties.max(), properties.rechargeInterval());
            }
        }
    }

    /**
     * 하트 행을 잠그고 DB 의 최신 값으로 읽습니다 (SELECT ... FOR UPDATE). 행이 없으면 null 입니다.
     * 같은 트랜잭션에서 앞서 읽어 둔 행이 있어도, 그사이 다른 트랜잭션이 돌려준 하트를 잃지 않게 다시 읽습니다.
     */
    private UserHeart lockedHeart(Long userId) {
        UserHeart heart = entityManager.find(UserHeart.class, userId);
        if (heart != null) {
            entityManager.refresh(heart, LockModeType.PESSIMISTIC_WRITE);
        }
        return heart;
    }

    /** 지울 게스트 계정들의 하트 행 · 사용 기록을 지웁니다. 계정을 지우기 전에 불러야 합니다 (users FK). */
    public void deleteAllByUserIds(Collection<Long> userIds) {
        heartUseRepository.deleteAllByUserIds(userIds);
        userHeartRepository.deleteAllByUserIds(userIds);
    }
}
