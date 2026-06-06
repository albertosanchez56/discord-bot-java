# syntax=docker/dockerfile:1.7

# Build stage --------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy POM first to leverage Maven dependency cache.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -e -ntp dependency:go-offline

# Copy sources and build the shaded jar.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -e -ntp -DskipTests package


# Runtime stage ------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=50"
ENV LOG_PATH=/app/logs

RUN mkdir -p /app/logs && \
    adduser --system --group --no-create-home bertini && \
    chown -R bertini:bertini /app

COPY --from=build /workspace/target/bertinibot-2.0.0.jar /app/app.jar
RUN chown bertini:bertini /app/app.jar

USER bertini

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
