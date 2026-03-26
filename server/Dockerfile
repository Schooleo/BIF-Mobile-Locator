FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .

RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /home/gradle/src/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
