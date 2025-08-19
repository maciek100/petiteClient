# --- Build stage ---
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy source
COPY . .

# Build the app
RUN ./mvnw clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy the built jar and any other required files
COPY --from=builder /build/target/*.jar app.jar
COPY --from=builder /build/urls.txt urls.txt

# Launch the app
CMD ["java", "-jar", "app.jar"]
