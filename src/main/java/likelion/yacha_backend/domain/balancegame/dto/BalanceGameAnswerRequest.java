package likelion.yacha_backend.domain.balancegame.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import likelion.yacha_backend.domain.balancegame.content.Choice;

/** 밸런스 게임 답 — 질문 순서대로 12개, 각각 {@code A} 또는 {@code B}. */
public record BalanceGameAnswerRequest(
        @NotNull @Size(min = 12, max = 12) List<@NotNull Choice> answers
) {
}
