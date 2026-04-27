# ====================== STAGE 1: Build ======================
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests -Dmaven.test.skip=true \
    -pl infra/config-server \
    -am

# ====================== STAGE 2: Runtime ======================
FROM eclipse-temurin:21-jre-jammy
VOLUME /tmp
WORKDIR /app
COPY --from=builder /app/infra/config-server/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]