package likelion.yacha_backend.domain.topic.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import likelion.yacha_backend.domain.topic.dto.CategoryResponse;
import likelion.yacha_backend.domain.topic.dto.TopicResponse;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.service.TopicService;
import likelion.yacha_backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "주제", description = "랜덤 주제 · 카테고리")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @Operation(
            summary = "랜덤 주제",
            description = """
                    고른 카테고리 안에서 주제 1개를 랜덤으로 줌
                    하위 카테고리를 먼저 랜덤으로 고른 뒤 그 안에서 고르므로, 하위 카테고리가 고르게 나옴

                    "다음 주제로 넘어가기"는 방금 본 주제 id 를 `exclude` 로 넘기면 됨

                    로그인 필요 (게스트 가능)
                    뽑을 주제가 없으면 `TOPIC_NOT_FOUND` (404), 카테고리 값이 잘못되면 `BINDING_ERROR` (400)
                    """)
    @GetMapping("/topics/random")
    public ApiResponse<TopicResponse> pickRandom(
            @RequestParam Category category,
            @RequestParam(required = false) Long exclude) {
        return ApiResponse.success(topicService.pickRandom(category, exclude));
    }

    @Operation(
            summary = "카테고리 목록",
            description = """
                    카테고리 8개와 하위 카테고리 (자동 제안 순환 순서)
                    `code` 는 요청에 쓰는 값, `name` 은 화면에 보여 줄 이름

                    로그인 없이 호출 가능
                    """)
    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(CategoryResponse.all());
    }
}
