package com.tbridge.debt;

import com.tbridge.common.env.DotEnv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;

@EnableScheduling
@SpringBootApplication(
        scanBasePackages = {"com.tbridge.debt", "com.tbridge.common"},
        exclude = UserDetailsServiceAutoConfiguration.class
)
public class DebtApplication {

    public static void main(String[] args) {
        DotEnv.load(Path.of("ms-debt/.env"), Path.of(".env"));
        boolean rabbit = Boolean.parseBoolean(System.getProperty("EVENTS_RABBIT",
                System.getenv().getOrDefault("EVENTS_RABBIT", "false")));
        SpringApplication app = new SpringApplication(DebtApplication.class);
        if (!rabbit) {
            app.setAdditionalProfiles("no-rabbit");
        }
        app.run(args);
    }
}
