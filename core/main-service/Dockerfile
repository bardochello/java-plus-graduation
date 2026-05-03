# ====================== STAGE 1: Build ======================
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .

# Собираем все необходимые модули, но полностью пропускаем тесты
RUN mvn clean package -DskipTests -Dmaven.test.skip=true \
    -pl stats/stats-dto,stats/stats-client,core/main-service \
    -am

# ====================== STAGE 2: Runtime ======================
FROM eclipse-temurin:21-jre-jammy
VOLUME /tmp
WORKDIR /app

COPY --from=builder /app/core/main-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]