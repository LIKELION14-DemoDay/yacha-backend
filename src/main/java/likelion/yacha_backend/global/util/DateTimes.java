package likelion.yacha_backend.global.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 응답에 싣는 시각 변환.
 *
 * <p>서버 · DB · JVM 은 모두 KST 로 통일했고 {@code LocalDateTime} 은 오프셋 없이 KST 값을 담습니다.
 * 오프셋 없이 내리면 프론트가 브라우저 시간대로 해석할 수 있으므로, 응답 시각에는 오프셋을 붙입니다
 * (예: {@code 2026-10-31T21:04:30+09:00}).
 */
public final class DateTimes {

    private DateTimes() {
    }

    /** {@code zone} 은 {@code Clock.getZone()} 을 넘깁니다. null 이면 null. */
    public static OffsetDateTime withOffset(LocalDateTime dateTime, ZoneId zone) {
        return dateTime == null ? null : dateTime.atZone(zone).toOffsetDateTime();
    }
}
