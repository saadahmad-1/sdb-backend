# Use official Gradle image for building
FROM gradle:8.5-jdk17 AS builder

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Build the application
RUN ./gradlew build

# Use slim JDK for runtime
FROM openjdk:17-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy built jar
COPY --from=builder /app/build/libs/*.jar app.jar

# Use build argument for Firebase credentials
ARG FIREBASE_SERVICE_ACCOUNT_JSON

# Create resources directory and write Firebase credentials
RUN mkdir -p /app/src/main/resources
RUN echo "$FIREBASE_SERVICE_ACCOUNT_JSON" > /app/src/main/resources/firebase-service-account.json

# Set environment variables for Docker
ENV FIREBASE_CREDENTIALS_PATH=/app/src/main/resources/firebase-service-account.json
ENV PORT=8080
ENV KTOR_ENV=production

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]