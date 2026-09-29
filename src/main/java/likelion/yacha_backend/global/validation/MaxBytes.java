package likelion.yacha_backend.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * UTF-8 로 인코딩했을 때의 <b>바이트 수</b> 상한.
 *
 * <p>{@code @Size} 는 <b>글자 수</b>를 셉니다. 한글은 한 글자가 3바이트라 둘이 다릅니다.
 * BCrypt 는 입력을 72<b>바이트</b>까지만 받고, 그보다 길면 {@code IllegalArgumentException} 을 던집니다.
 *
 * <pre>
 *   "가".repeat(30)  →  30자 / 90바이트
 *      {@code @Size(max = 72)} 통과 → encode() 에서 예외 → 500
 * </pre>
 *
 * <p>그래서 비밀번호처럼 BCrypt 로 들어가는 값은 이 검증으로 <b>400</b> 에서 막습니다. (코드 리뷰 반영)
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxBytesValidator.class)
public @interface MaxBytes {

    int value();

    String message() default "{value}바이트를 넘을 수 없습니다. (한글은 한 글자가 3바이트입니다)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
