package likelion.yacha_backend.infra.liner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinerSearchRequest(

        String query,

        @JsonProperty("country_code")
        String countryCode,

        String lang,

        @JsonProperty("date_range")
        String dateRange,

        @JsonProperty("max_results")
        Integer maxResults

) {
}