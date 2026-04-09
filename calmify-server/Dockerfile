# Build stage
FROM gradle:8.13-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :calmify-server:buildFatJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/calmify-server/build/libs/calmify-server-all.jar server.jar

EXPOSE 8080

# Cloud Run sets PORT env var
ENV PORT=8080

CMD ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "server.jar"]
