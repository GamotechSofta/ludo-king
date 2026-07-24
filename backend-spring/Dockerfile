# Build stage
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/ludo-backend-0.0.1-SNAPSHOT.jar app.jar

# Render injects PORT; Spring reads server.port=${PORT:3000}
ENV PORT=3000
EXPOSE 3000

ENTRYPOINT ["java", "-jar", "app.jar"]
