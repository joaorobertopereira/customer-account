package br.com.helpcsistemas.customeraccount.customer.adapter.out.mail;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkOtpDeliveryPort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class SubjectLinkOtpMailAdapter implements SubjectLinkOtpDeliveryPort {

    private final JavaMailSender mailSender;
    private final String from;

    SubjectLinkOtpMailAdapter(JavaMailSender mailSender, @Value("${app.subject-link.mail-from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String email, String code, Duration ttl) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Confirmação de vínculo da sua conta");
        message.setText("""
                Olá,

                Seu código para vincular o acesso ao cadastro existente é: %s
                Ele expira em %d minutos. Se você não solicitou este código, ignore esta mensagem.
                Nunca compartilhe este código com ninguém.
                """.formatted(code, ttl.toMinutes()));
        mailSender.send(message);
    }
}
