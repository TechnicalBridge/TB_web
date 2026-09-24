package com.tbridge.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ms-payments: el cobro.
 *
 * <p>Abre el cobro con el monto que dice ms-debt (nunca el del navegador),
 * guarda cada paso en un libro que solo crece, fija los pesos de una deuda en
 * UF al abrir el cobro, y le avisa a ms-debt cuando el pago se concreta.
 */
@SpringBootApplication(
        scanBasePackages = {"com.tbridge.payments", "com.tbridge.common"},
        exclude = UserDetailsServiceAutoConfiguration.class
)
//  El despachador de avisos y la carga diaria de la UF corren en segundo plano.
@EnableScheduling
public class PaymentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsApplication.class, args);
    }
}
