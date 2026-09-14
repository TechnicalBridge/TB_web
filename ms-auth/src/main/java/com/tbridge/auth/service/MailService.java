package com.tbridge.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean smtpEnabled;

    public MailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.from-email}") String from,
            @Value("${spring.mail.host:}") String host
    ) {
        this.mailSender = mailSender.getIfAvailable();
        this.from = from;
        this.smtpEnabled = this.mailSender != null && StringUtils.hasText(host);
    }

    public void sendMagicLink(String to, String url) {
        log.info("SMTP magic-link para {}: {}", to, url);
        if (!smtpEnabled) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Tu acceso a Technical Bridge");
            message.setText("""
                    Hola,

                    Entra a tu sesión con este enlace de un solo uso (válido 15 minutos):

                    %s

                    Si no lo pediste, ignora este correo.

                    Technical Bridge
                    """.formatted(url));
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("No se pudo enviar SMTP a {}: {}", to, e.getMessage());
        }
    }
}
