package com.digitalbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DigitalBotApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(DigitalBotApplication.class, args);
    }

    private static void loadDotEnv() {
        for (Path path : List.of(Path.of(".env"), Path.of("backend/.env"))) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(path)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    String key = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1).trim();
                    if (System.getenv(key) == null && System.getProperty(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            } catch (Exception ignored) {
                // keep defaults from application.yml
            }
            break;
        }
    }
}
