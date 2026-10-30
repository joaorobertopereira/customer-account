package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.otp;

import br.com.helpcsistemas.customeraccount.privacy.BlindIndex;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvider;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.otp.OtpChallengeStore.OtpChallenge;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * OTP próprio por e-mail: código de 6 dígitos (SecureRandom), armazenado apenas como HMAC (chave do blind index) e
 * comparado em tempo constante. Tentativas e expiração são controladas pelo agregado {@link VerificationProcess}.
 */
class OwnEmailOtpProvider implements VerificationProvider {

    static final ProviderId ID = new ProviderId("own-email-otp");
    static final Duration TTL = VerificationProcess.DEFAULT_CHALLENGE_TTL;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeStore challenges;
    private final OtpMailSender mail;
    private final BlindIndex hmac;
    private final Clock clock;

    OwnEmailOtpProvider(OtpChallengeStore challenges, OtpMailSender mail, BlindIndex hmac, Clock clock) {
        this.challenges = challenges;
        this.mail = mail;
        this.hmac = hmac;
        this.clock = clock;
    }

    @Override
    public ProviderId id() {
        return ID;
    }

    @Override
    public Set<VerificationType> supportedTypes() {
        return Set.of(VerificationType.EMAIL_OWNERSHIP);
    }

    @Override
    public ProviderResult verify(ProviderRequest request) {
        var challengeId = UUID.randomUUID().toString();
        var code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        challenges.save(new OtpChallenge(
                challengeId,
                request.verificationId().toString(),
                hash(challengeId, code),
                clock.instant().plus(TTL)));
        mail.sendCode(request.subject().email(), code, TTL);
        return new ProviderResult(
                ProviderDecision.CHALLENGE_SENT, null, List.of(), challengeId, 0, "{\"channel\":\"email\"}");
    }

    @Override
    public ProviderResult confirm(ProviderRequest request, String transactionId, String answer) {
        var challenge = challenges
                .find(transactionId)
                .filter(c -> c.verificationId().equals(request.verificationId().toString()))
                .filter(c -> clock.instant().isBefore(c.expiresAt()));
        if (challenge.isEmpty()) {
            return rejected("CHALLENGE_NOT_FOUND");
        }
        var expected = challenge.get().codeHash().getBytes(StandardCharsets.UTF_8);
        var actual = hash(transactionId, answer).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            return rejected("INVALID_CODE");
        }
        challenges.delete(transactionId);
        return new ProviderResult(ProviderDecision.APPROVED, 100, List.of(), transactionId, 0, "{\"match\":true}");
    }

    private String hash(String challengeId, String code) {
        return hmac.compute("otp", challengeId + ":" + code);
    }

    private static ProviderResult rejected(String reason) {
        return new ProviderResult(ProviderDecision.REJECTED, 0, List.of(reason), null, 0, "{\"match\":false}");
    }
}
