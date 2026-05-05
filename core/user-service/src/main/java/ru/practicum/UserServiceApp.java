package ru.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import ru.practicum.config.DatabaseInitializer;

@SpringBootApplication
@EnableDiscoveryClient
public class UserServiceApp {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(UserServiceApp.class);
        app.addListeners(new DatabaseInitializer());
        app.run(args);
    }
}