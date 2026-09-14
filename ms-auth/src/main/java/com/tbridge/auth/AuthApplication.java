package com.tbridge.auth;

import com.tbridge.common.env.DotEnv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.nio.file.Path;

@SpringBootApplication(
        scanBasePackages = {"com.tbridge.auth", "com.tbridge.common"},
        exclude = UserDetailsServiceAutoConfiguration.class
)
public class AuthApplication {

    public static void main(String[] args) {
        DotEnv.load(Path.of("ms-auth/.env"), Path.of(".env"));
        SpringApplication.run(AuthApplication.class, args);
    }
}
