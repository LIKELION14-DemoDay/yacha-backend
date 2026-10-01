# 1. Spring Boot 애플리케이션 빌드
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

COPY src src

RUN ./gradlew clean bootJar --no-daemon


# 2. 빌드된 jar 실행
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# 서버 · DB · JVM 시간대를 KST 로 통일합니다. 베이스 이미지의 기본값은 UTC 라, 지정하지 않으면
# Clock · JPA Auditing 이 UTC 로 찍히고 응답 시각의 오프셋도 +00:00 이 됩니다.
ENV TZ=Asia/Seoul

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]