# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Cache dependencies first (layer is reused unless pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# Copy source and build (skip tests; tests run in CI)
COPY src ./src
RUN mvn clean package -DskipTests -B --no-transfer-progress

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="unqueryservice"
LABEL description="Multi-database unified query service powered by Apache Calcite"

WORKDIR /app

# Create a non-root user for running the application
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Copy the built jar from the build stage
COPY --from=builder /workspace/target/*.jar app.jar

# Own the app directory by the non-root user
RUN chown -R appuser:appgroup /app

USER appuser

# Expose the HTTP port
EXPOSE 8080

# JVM tuning: prefer G1GC, cap heap, enable container awareness
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
