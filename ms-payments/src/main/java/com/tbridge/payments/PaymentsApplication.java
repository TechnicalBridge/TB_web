package com.tbridge.payments;

import com.tbridge.common.env.DotEnv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.nio.file.Path;

@SpringBootApplication(
        scanBasePackages = {"com.tbridge.payments", "com.tbridge.common"},
        exclude = UserDetailsServiceAutoConfiguration.class
)
public class PaymentsApplication {

    public static void main(String[] args) {
        DotEnv.load(Path.of("ms-payments/.env"), Path.of(".env"));
        boolean rabbit = Boolean.parseBoolean(System.getProperty("EVENTS_RABBIT",
                System.getenv().getOrDefault("EVENTS_RABBIT", "false")));
        SpringApplication app = new SpringApplication(PaymentsApplication.class);
        if (!rabbit) {
            app.setAdditionalProfiles("no-rabbit");
        }
        app.run(args);
    }
}
