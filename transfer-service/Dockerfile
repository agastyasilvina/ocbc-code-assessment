FROM maven:3.9.16-eclipse-temurin-21
WORKDIR /app
COPY . .
RUN mvn -B -DskipTests package
EXPOSE 8080
ENTRYPOINT ["java", "-Xms1g", "-Xmx1g", "-XX:+AlwaysPreTouch", "-jar", "transfer-service/target/transfer-service-1.0.0-SNAPSHOT.jar"]
