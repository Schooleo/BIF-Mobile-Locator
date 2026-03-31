FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .

RUN gradle --no-daemon clean bootJar

FROM eclipse-temurin:21-jre

RUN addgroup --system app && adduser --system --ingroup app app

WORKDIR /app

COPY --chown=app:app --from=builder /home/gradle/src/build/libs/app.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]