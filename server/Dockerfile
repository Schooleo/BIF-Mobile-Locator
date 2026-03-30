FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .

RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre

RUN addgroup --system app && adduser --system --ingroup app app

WORKDIR /app

# Using *-SNAPSHOT.jar to avoid pulling in the '-plain.jar' file. 
COPY --chown=app:app --from=builder /home/gradle/src/build/libs/*-SNAPSHOT.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]