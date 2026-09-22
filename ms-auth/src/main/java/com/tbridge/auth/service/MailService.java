package com.tbridge.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * El envio por correo.
 *
 * <p>Sin SMTP configurado no falla: registra el contenido en el log, que es lo
 * que permite trabajar y demostrar el flujo sin depender de un servidor de
 * correo.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean smtpEnabled;
    private final String publicUrl;

    public MailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.from-email}") String from,
            @Value("${app.public-url}") String publicUrl,
            @Value("${spring.mail.host:}") String host
    ) {
        this.mailSender = mailSender.getIfAvailable();
        this.from = from;
        this.publicUrl = publicUrl.replaceAll("/$", "");
        this.smtpEnabled = this.mailSender != null && StringUtils.hasText(host);
    }

    /**
     * El correo con el codigo.
     *
     * <p>Fijate en lo que NO lleva: ningun enlace. El deudor entra escribiendo
     * la direccion, y por eso el mensaje no le sirve a un phisher para
     * llevarlo a otra parte. Tampoco lleva el detalle de la deuda: eso vive
     * dentro del portal, asi que un mensaje que llegue a un numero equivocado
     * no expone cuanto debe alguien ni a quien.
     */
    public void enviarCodigo(String to, String codigo, String acreedor) {
        log.info("Codigo de acceso para {}: {}", to, codigo);
        enviar(to, "Tu codigo de acceso", """
                Hola,

                %s te dejo un mensaje sobre un pago pendiente.

                Para verlo, entra a %s y escribe tu RUT y este codigo:

                    %s

                No hay ningun enlace en este correo a proposito: entra
                escribiendo la direccion tu mismo. Asi puedes verificar donde
                estas antes de poner cualquier dato.

                Technical Bridge
                """.formatted(acreedor == null ? "Una empresa" : acreedor, publicUrl, codigo));
    }

    /** El camino de excepcion, para quien no logra entrar con el codigo. */
    public void enviarEnlace(String to, String url, long minutos) {
        log.info("Enlace de acceso para {}: {}", to, url);
        enviar(to, "Tu acceso a Technical Bridge", """
                Hola,

                Entra con este enlace de un solo uso, valido %d minutos:

                %s

                Si no lo pediste, ignora este correo.

                Technical Bridge
                """.formatted(minutos, url));
    }

    private void enviar(String to, String asunto, String cuerpo) {
        if (!smtpEnabled) {
            return;
        }
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(from);
            mensaje.setTo(to);
            mensaje.setSubject(asunto);
            mensaje.setText(cuerpo);
            mailSender.send(mensaje);
        } catch (Exception e) {
            log.warn("No se pudo enviar SMTP a {}: {}", to, e.getMessage());
        }
    }
}
