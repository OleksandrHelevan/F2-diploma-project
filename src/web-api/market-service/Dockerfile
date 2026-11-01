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

COPY market-service/src market-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl market-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/market-service/target/market-service-*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
