package br.com.helpcsistemas.customeraccount.verification.adapter.codec;

import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.FALLBACK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.PRIMARY;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.TENANT;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.otpRule;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.result;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.helpcsistemas.customeraccount.eventsourcing.NewEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.TenantVerificationPolicy;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class VerificationEventCodecTest {

    private final VerificationEventCodec codec =
            new VerificationEventCodec(JsonMapper.builder().build(), new XorCipher());

    @Test
    void roundTripsEveryProcessEventAndEncryptsProviderData() {
        var customerId = UUID.randomUUID();
        var process = VerificationProcess.request(
                VerificationId.newId(), TENANT, customerId, rule(PRIMARY, FALLBACK), 2, "app", CLOCK);
        process.submit(CLOCK);
        process.providerFailed("TIMEOUT", 30, CLOCK);
        process.submit(CLOCK);
        process.responseReceived(result(ProviderDecision.APPROVED, 88), CLOCK);

        for (var event : process.pendingEvents()) {
            var encoded = codec.encodeProcess(event, customerId);
            assertThat(codec.decodeProcess(stored(encoded))).isEqualTo(event);
            if (encoded.type().startsWith("ProviderResponseReceived")
                    || encoded.type().startsWith("VerificationConcluded")) {
                assertThat(encoded.json()).isFalse();
                assertThat(new String(encoded.data(), StandardCharsets.UTF_8)).doesNotContain("tx-1");
            }
        }
    }

    @Test
    void roundTripsChallengeAndDeliveryEvents() {
        var customerId = UUID.randomUUID();
        var process = VerificationProcess.request(
                VerificationId.newId(), TENANT, customerId, otpRule(PRIMARY, null), 1, "app", CLOCK);
        process.submit(CLOCK);
        process.responseReceived(result(ProviderDecision.CHALLENGE_SENT, null), CLOCK);
        process.answerChallenge(result(ProviderDecision.REJECTED, null), CLOCK);
        process.answerChallenge(result(ProviderDecision.APPROVED, null), CLOCK);
        process.markDeliveryFailed(8, 503, CLOCK);

        for (var event : process.pendingEvents()) {
            var encoded = codec.encodeProcess(event, customerId);
            assertThat(codec.decodeProcess(stored(encoded))).isEqualTo(event);
        }
        assertThat(codec.isConclusion(
                        stored(codec.encodeProcess(process.pendingEvents().getLast(), customerId))))
                .isFalse();
    }

    @Test
    void roundTripsPolicyChanges() {
        var policy = TenantVerificationPolicy.empty(TENANT);
        policy.configure(rule(PRIMARY, FALLBACK), "admin", "inicial", CLOCK);
        policy.configure(rule(FALLBACK, null), "admin", "troca", CLOCK);

        for (var event : policy.pendingEvents()) {
            assertThat(codec.decodePolicy(stored(codec.encodePolicy(event)))).isEqualTo(event);
        }
    }

    private static StoredEvent stored(NewEvent event) {
        return new StoredEvent(
                "verification-x", 0, event.eventId(), event.type(), event.data(), event.metadata(), CLOCK.instant());
    }

    private static final class XorCipher implements PersonalDataCipher {

        @Override
        public byte[] encrypt(String subjectId, byte[] plaintext) {
            return xor(plaintext);
        }

        @Override
        public byte[] decrypt(String subjectId, byte[] ciphertext) {
            return xor(ciphertext);
        }

        private static byte[] xor(byte[] input) {
            var output = new byte[input.length];
            for (int i = 0; i < input.length; i++) {
                output[i] = (byte) (input[i] ^ 0x5A);
            }
            return output;
        }
    }
}
