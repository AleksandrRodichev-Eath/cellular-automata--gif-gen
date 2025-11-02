FROM gradle:8.8-jdk21-alpine AS build
WORKDIR /workspace

COPY gradle gradle
COPY gradlew .
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY src src

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TELEGRAM_BOT_TOKEN="" \
    TELEGRAM_CHAT_ID=""

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
