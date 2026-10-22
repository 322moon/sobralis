FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --console=plain bootJar

FROM eclipse-temurin:21-jdk
WORKDIR /app
RUN groupadd --system sobralis && useradd --system --gid sobralis sobralis
COPY --from=build /workspace/build/libs/sobralis.jar app.jar
USER sobralis
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
