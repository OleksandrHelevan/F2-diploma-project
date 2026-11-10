# Build context: src/web-api (the multi-module root)
# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# The reactor needs every module's pom, even when building only one service
COPY pom.xml .
COPY common/pom.xml common/
COPY user-service/pom.xml user-service/
COPY market-service/pom.xml market-service/
COPY chat-service/pom.xml chat-service/
COPY notification-service/pom.xml notification-service/

COPY common/src common/src
COPY user-service/src user-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl user-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/user-service/target/user-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
