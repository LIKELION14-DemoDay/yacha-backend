package likelion.yacha_backend.domain.payment.service;

import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Paddle 웹훅 입구. 서명 확인 · 본문 읽기는 트랜잭션 밖에서 해서, 위조 요청이 DB 커넥션을 잡지 않게 한다.
 * 저장 · 반영은 {@link PaddleEventProcessor}.
 */
@Service
@RequiredArgsConstructor
public class PaddleWebhookService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PaddleWebhookVerifier verifier;
    private final PaddleEventProcessor processor;

    public void handle(String signatureHeader, byte[] rawBody) {
        verifier.verify(signatureHeader, rawBody);

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(rawBody);
        } catch (JacksonException e) {
            throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_PAYLOAD);
        }
        processor.process(root, rawBody);
    }
}
