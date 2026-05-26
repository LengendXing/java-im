FROM gradle:8.7-jdk17 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY protocol/ protocol/
COPY server/ server/
RUN gradle :server:fatJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/*-all.jar im-server.jar
EXPOSE 8800 8801 8080
ENTRYPOINT ["java", "-jar", "im-server.jar"]
