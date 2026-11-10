package likelion.yacha_backend.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import likelion.yacha_backend.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 결제 로그. 서명이 맞는 Paddle 웹훅은 처리하지 않는 이벤트까지 원본 그대로 남긴다.
 * 분쟁이 생겼을 때 "그때 Paddle 이 무엇을 보냈고 우리가 어떻게 처리했는지"의 근거. 지우지 않는다.
 *
 * <p>같은 이벤트는 event_id 로 한 번만 저장 · 처리한다 (Paddle 재전송).
 */
@Entity
@Table(name = "payment_events", indexes = @Index(name = "idx_payment_events_transaction", columnList = "transaction_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Paddle 이벤트 ID ({@code evt_...}) */
    @Column(name = "event_id", nullable = false, unique = true, length = 50)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** 관련 거래 ID. 거래와 상관없는 이벤트면 NULL */
    @Column(name = "transaction_id", length = 50)
    private String transactionId;

    /** Paddle 이 이벤트가 일어났다고 보낸 시각 (RFC 3339 문자열 그대로) */
    @Column(name = "occurred_at", length = 40)
    private String occurredAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentEventResult result;

    /** 받은 본문 원본 */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String payload;

    public static PaymentEvent of(String eventId, String eventType, String transactionId,
                                  String occurredAt, PaymentEventResult result, String payload) {
        PaymentEvent event = new PaymentEvent();
        event.eventId = eventId;
        event.eventType = eventType;
        event.transactionId = transactionId;
        event.occurredAt = occurredAt;
        event.result = result;
        event.payload = payload;
        return event;
    }
}
