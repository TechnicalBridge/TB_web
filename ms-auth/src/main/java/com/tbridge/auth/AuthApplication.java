package com.tbridge.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * ms-auth: como entra cada quien al portal.
 *
 * <p>El deudor, con su RUT y un codigo de acceso; el personal de una empresa,
 * con un enlace al correo. Emite el JWT de la sesion y la llave de renovacion.
 */
@SpringBootApplication(
        scanBasePackages = {"com.tbridge.auth", "com.tbridge.common"},
        //  Sin usuarios en memoria: nadie entra con usuario y contrasena.
        exclude = UserDetailsServiceAutoConfiguration.class
)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
