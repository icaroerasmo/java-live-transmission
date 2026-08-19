# syntax=docker/dockerfile:1

# ---- build stage ----
FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-21 AS build
ARG TARGETPLATFORM
ARG BUILDPLATFORM
WORKDIR /app
COPY src ./src/
COPY pom.xml .
RUN mvn clean package -DskipTests

# ---- runtime stage ----
FROM archlinux:latest
ARG TZ=America/Bahia
RUN pacman -Syu --noconfirm --needed ffmpeg jre21-openjdk-headless fontconfig translate-shell tzdata \
    && pacman -Scc --noconfirm
RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
RUN mkdir -p /app/config /app/data
COPY --from=build /app/target/java-live-transmission.jar /app/java-live-transmission.jar
ENTRYPOINT [ "java", "-Dspring.config.additional-location=optional:/app/config/config.yaml", "-jar", "/app/java-live-transmission.jar" ]
