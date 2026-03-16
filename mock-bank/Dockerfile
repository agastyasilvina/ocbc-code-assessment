# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/mock-bank.jar /app/mock-bank.jar
USER 10001:10001
EXPOSE 9090
HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=12 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/9090 && printf "GET /health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q UP <&3'
ENTRYPOINT ["java", "-jar", "/app/mock-bank.jar"]
