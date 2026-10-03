package likelion.yacha_backend.global.security.stomp;

import likelion.yacha_backend.global.security.jwt.AuthUser;

/**
 * {@code /user/queue/**} 밖의 목적지를 구독해도 되는지 판단합니다.
 *
 * <p>토론방처럼 도메인 데이터를 봐야 판단할 수 있는 목적지는 그 도메인이 이 인터페이스를 구현해 빈으로 등록합니다.
 * {@code global} 이 {@code domain} 을 참조하지 않도록 판단 로직을 밖에 둡니다.
 * {@link StompAuthChannelInterceptor} 는 목적지를 맡는 구현이 없거나 거부하면 {@code FORBIDDEN} 으로 막습니다.
 */
public interface StompSubscriptionAuthorizer {

    /** 이 목적지를 이 구현이 판단하는가. */
    boolean supports(String destination);

    /** 구독을 허용하면 true. {@link #supports} 가 true 인 목적지만 들어옵니다. */
    boolean canSubscribe(AuthUser user, String destination);
}
