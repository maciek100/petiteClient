FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

COPY . .

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar
COPY --from=builder /build/urls.txt urls.txt

CMD ["java", "-jar", "app.jar"]