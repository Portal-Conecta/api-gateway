# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

ARG MAVEN_USERNAME

COPY .mvn/ .mvn/
COPY pom.xml .

RUN --mount=type=secret,id=maven_password \
    if [ -z "$MAVEN_USERNAME" ]; then echo "MAVEN_USERNAME is required to download portal-logging from GitHub Packages." >&2; exit 1; fi && \
    if [ ! -s /run/secrets/maven_password ]; then echo "MAVEN_PASSWORD is required to download portal-logging from GitHub Packages." >&2; exit 1; fi && \
    MAVEN_PASSWORD="$(cat /run/secrets/maven_password)" mvn dependency:go-offline -B

COPY src ./src

RUN --mount=type=secret,id=maven_password \
    if [ -z "$MAVEN_USERNAME" ]; then echo "MAVEN_USERNAME is required to download portal-logging from GitHub Packages." >&2; exit 1; fi && \
    if [ ! -s /run/secrets/maven_password ]; then echo "MAVEN_PASSWORD is required to download portal-logging from GitHub Packages." >&2; exit 1; fi && \
    MAVEN_PASSWORD="$(cat /run/secrets/maven_password)" mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
