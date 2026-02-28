# ============================================
# Multi-stage Dockerfile for Quant Platform
# Stage 1: Build with Maven
# Stage 2: Run with JRE 21 slim
# ============================================

# -- Build stage --
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B

# -- Runtime stage --
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

# Expose ports: 8080 (HTTP), 8081 (actuator)
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
