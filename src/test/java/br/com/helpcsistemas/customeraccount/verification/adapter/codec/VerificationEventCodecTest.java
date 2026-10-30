package br.com.helpcsistemas.customeraccount.verification.adapter.codec;

import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.FALLBACK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.PRIMARY;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.TENANT;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.identityRule;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.otpRule;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.result;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.helpcsistemas.customeraccount.eventsourcing.NewEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResultStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.TenantVerificationPolicy;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    void roundTripsHostedEventsAndEncryptsLinkAndProviderResult() {
        var customerId = UUID.randomUUID();
        var process = VerificationProcess.request(
                VerificationId.newId(), TENANT, customerId, identityRule(PRIMARY, FALLBACK), 2, "app", CLOCK);
        process.issueHostedLink(
                "session-secret",
                "https://provider.example/private-token",
                CLOCK.instant().plusSeconds(3600),
                java.util.List.of("cpf"),
                CLOCK);
        process.receiveHostedResult(
                new HostedResult(
                        "session-secret",
                        HostedResultStatus.COMPLETED,
                        95,
                        java.util.List.of(),
                        java.util.List.of(),
                        "event-1",
                        CLOCK.instant(),
                        "provider-raw"),
                CLOCK);

        for (var event : process.pendingEvents()) {
            var encoded = codec.encodeProcess(event, customerId);
            assertThat(codec.decodeProcess(stored(encoded))).isEqualTo(event);
            if (encoded.type().startsWith("VerificationLinkIssued")
                    || encoded.type().startsWith("ProviderResultReceived")) {
                assertThat(encoded.json()).isFalse();
                assertThat(new String(encoded.data(), StandardCharsets.UTF_8))
                        .doesNotContain("private-token", "provider-raw");
            }
        }
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

    @Test
    void readsLegacyV1PolicyAndConclusionPayloads() {
        var legacyRule = new VerificationPayloads.RuleV1(
                br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType.FACE_MATCH,
                PRIMARY.value(),
                FALLBACK.value(),
                70,
                br.com.helpcsistemas.customeraccount.verification.domain.model.InconclusiveAction.MANUAL_REVIEW,
                true);
        var policyPayload = new VerificationPayloads.RuleConfiguredV1(
                TENANT.value(), legacyRule, null, "admin", "historical", CLOCK.instant());
        var decodedPolicy = codec.decodePolicy(stored(NewEvent.json(
                VerificationEventCodec.RULE_CONFIGURED_V1,
                JsonMapper.builder().build().writeValueAsBytes(policyPayload),
                Map.of())));
        assertThat(decodedPolicy)
                .isInstanceOf(
                        br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured
                                .class);

        var verificationId = VerificationId.newId();
        var conclusion = new VerificationPayloads.ConcludedV1(
                verificationId.value(),
                br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationOutcome.APPROVED,
                PRIMARY.value(),
                90,
                List.of(),
                "tx-legacy",
                Instant.parse("2025-01-01T00:00:00Z"));
        var storedConclusion = new StoredEvent(
                "verification-" + verificationId,
                1,
                UUID.randomUUID(),
                VerificationEventCodec.CONCLUDED_V1,
                JsonMapper.builder().build().writeValueAsBytes(conclusion),
                Map.of(
                        VerificationEventCodec.META_CUSTOMER_ID,
                        UUID.randomUUID().toString(),
                        VerificationEventCodec.META_ENCRYPTED,
                        "false"),
                CLOCK.instant());

        var decodedConclusion = (br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationConcluded)
                codec.decodeProcess(storedConclusion);
        assertThat(decodedConclusion.validUntil()).isNull();
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
