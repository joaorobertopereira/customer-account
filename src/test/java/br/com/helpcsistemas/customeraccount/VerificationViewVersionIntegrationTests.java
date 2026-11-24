package br.com.helpcsistemas.customeraccount;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationViewRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Projeção de validações com várias instâncias: uma versão antiga nunca sobrescreve uma mais nova. */
@Testcontainers(disabledWithoutDocker = true)
class VerificationViewVersionIntegrationTests extends IntegrationTestSupport {

    @Autowired
    VerificationViewRepositoryPort views;

    @Test
    void olderVersionDoesNotOverwriteNewerOne() {
        var id = VerificationId.newId();
        var customerId = UUID.randomUUID();

        views.save(view(id, customerId, VerificationStatus.PENDING, "https://hosted.example/link", 1));
        views.save(view(id, customerId, VerificationStatus.COMPLETED, null, 3));
        views.save(view(id, customerId, VerificationStatus.PENDING, "https://hosted.example/link", 2));

        var stored = views.findById(id).orElseThrow();
        assertThat(stored.version()).isEqualTo(3);
        assertThat(stored.status()).isEqualTo(VerificationStatus.COMPLETED);
        // A substituição é integral: o link que voltou a nulo não fica velho no documento.
        assertThat(stored.hostedLink()).isNull();
    }

    private static VerificationView view(
            VerificationId id, UUID customerId, VerificationStatus status, String link, long version) {
        var now = Instant.parse("2026-11-24T12:00:00Z");
        return new VerificationView(
                id,
                TenantId.of("tenant-view-version"),
                customerId,
                VerificationType.IDENTITY,
                status,
                null,
                ProviderId.of("legitimuz"),
                null,
                List.of(),
                null,
                0,
                false,
                List.of(),
                now,
                null,
                null,
                0,
                null,
                false,
                link,
                link == null ? null : now.plusSeconds(600),
                null,
                false,
                false,
                version);
    }
}
