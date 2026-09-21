package likelion.yacha_backend;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class YachaBackendApplication {

	/**
	 * EC2 등 서버 기본 타임존은 UTC 라서, 그대로 두면 @CreatedDate 로 찍히는
	 * createdAt 이 실제 시각보다 9시간 이릅니다. JVM 기본 타임존을 KST 로 고정합니다.
	 * 토론 구간 시각(started_at 기준)과 topic_date 계산도 이 값을 따릅니다.
	 */
	@PostConstruct
	public void setDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(YachaBackendApplication.class, args);
	}

}
