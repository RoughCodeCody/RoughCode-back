FROM openjdk:11-jdk-slim as builder

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src


RUN chmod +x ./gradlew
RUN ./gradlew bootJar --warning-mode all

FROM openjdk:11-jdk-slim
COPY --from=builder build/libs/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/app.jar"]