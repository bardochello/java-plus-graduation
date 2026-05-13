FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests -Dmaven.test.skip=true \
    -pl stats/serialization,stats/analyzer \
    -am

FROM eclipse-temurin:21-jre-jammy
VOLUME /tmp
WORKDIR /app

COPY --from=builder /app/stats/analyzer/target/*.jar app.jar

EXPOSE 9091

ENTRYPOINT ["java", "-jar", "app.jar"]
