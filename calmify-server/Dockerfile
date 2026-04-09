# Build stage
FROM gradle:8.13-jdk17 AS build

# Install minimal Android SDK (needed for Gradle to configure KMP modules)
ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip && \
    yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 && \
    ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
        "platforms;android-36" \
        "build-tools;36.0.0" > /dev/null 2>&1

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
