FROM gradle:8.7-jdk17-alpine AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew .
COPY gradle.properties .
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY qubitlock-core/build.gradle.kts qubitlock-core/
COPY qubitlock-starter-ktor/build.gradle.kts qubitlock-starter-ktor/
COPY qubitlock-app/build.gradle.kts qubitlock-app/

RUN ./gradlew dependencies --no-daemon

COPY . .

RUN ./gradlew :qubitlock-app:fatJar --no-daemon

FROM openjdk:17-jdk-slim
WORKDIR /app

COPY --from=build /app/qubitlock-app/build/libs/qubitlock-app-*-standalone.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
