FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests -Dmaven.test.skip=true \
    -pl core/user-service \
    -am

FROM eclipse-temurin:21-jre-jammy
VOLUME /tmp
WORKDIR /app

COPY --from=builder /app/core/user-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
