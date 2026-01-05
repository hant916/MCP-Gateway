# Multi-stage Dockerfile for MCP Gateway
# Stage 1: Build
FROM maven:3.8.8-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 mcpgateway && \
    adduser -D -u 1001 -G mcpgateway mcpgateway

# Copy JAR from build stage
COPY --from=build /app/target/mcpgateway-spring-*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R mcpgateway:mcpgateway /app

# Switch to non-root user
USER mcpgateway

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Xms256m", \
    "-Xmx512m", \
    "-jar", \
    "app.jar"]
