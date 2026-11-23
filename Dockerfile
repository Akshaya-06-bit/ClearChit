# Build the Spring Boot application
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# Small runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
# Bidding windows are naive local date-times typed in the browser. Run the JVM
# in the same zone as your users (change if you are not in India), otherwise
# windows are shifted by the UTC offset.
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Kolkata"
RUN useradd --system --uid 10001 spring
COPY --from=build /app/target/ClearChit-0.0.1-SNAPSHOT.jar app.jar
USER spring
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
