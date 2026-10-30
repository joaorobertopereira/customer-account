package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.otp;

import br.com.helpcsistemas.customeraccount.privacy.BlindIndex;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvider;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvidersProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.verification.providers.own-email-otp.enabled", havingValue = "true")
class OwnEmailOtpConfig {

    @Bean
    VerificationProvider ownEmailOtpProvider(
            OtpChallengeStore challenges,
            JavaMailSender mailSender,
            BlindIndex blindIndex,
            Clock clock,
            VerificationProvidersProperties properties) {
        var mail = new OtpMailSender(mailSender, properties.ownEmailOtp().mailFrom());
        return new OwnEmailOtpProvider(challenges, mail, blindIndex, clock);
    }
}
