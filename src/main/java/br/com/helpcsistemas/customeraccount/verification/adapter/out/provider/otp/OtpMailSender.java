package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.otp;

import java.time.Duration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Envia o código ao e-mail do titular. O código nunca é logado. */
class OtpMailSender {

    private final JavaMailSender mailSender;
    private final String from;

    OtpMailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    void sendCode(String to, String code, Duration ttl) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Seu código de verificação");
        message.setText("""
                Olá,

                Seu código de verificação é: %s

                Ele expira em %d minutos. Se você não solicitou este código, ignore esta mensagem.
                Nunca compartilhe este código com ninguém.
                """.formatted(code, ttl.toMinutes()));
        mailSender.send(message);
    }
}
