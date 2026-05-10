package ru.practicum.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Создаёт базу данных для user-service до старта Spring контекста.
 * Срабатывает на этапе подготовки Environment — до инициализации DataSource.
 * Это позволяет сервису корректно запускаться в средах где БД не создана заранее.
 */
@Slf4j
public class DatabaseInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();

        String url      = env.getProperty("spring.datasource.url", "");
        String username = env.getProperty("spring.datasource.username", "postgres");
        String password = env.getProperty("spring.datasource.password", "");

        String dbName = extractDatabaseName(url);
        if (dbName == null || dbName.isBlank() || dbName.equals("postgres")) {
            return;
        }

        String postgresUrl = url.substring(0, url.lastIndexOf('/')) + "/postgres";

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            log.warn("PostgreSQL драйвер не найден, пропускаем создание БД");
            return;
        }

        try (Connection conn = DriverManager.getConnection(postgresUrl, username, password);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'");

            if (!rs.next()) {
                log.info("БД '{}' не существует, создаём...", dbName);
                stmt.executeUpdate("CREATE DATABASE \"" + dbName + "\"");
                log.info("БД '{}' успешно создана", dbName);
            } else {
                log.info("БД '{}' уже существует", dbName);
            }

        } catch (Exception e) {
            log.warn("Не удалось создать БД '{}': {}. Возможно нет прав или БД уже есть.",
                    dbName, e.getMessage());
        }
    }

    private String extractDatabaseName(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            String part = url.substring(url.lastIndexOf('/') + 1);
            int q = part.indexOf('?');
            return q >= 0 ? part.substring(0, q) : part;
        } catch (Exception e) {
            return null;
        }
    }
}