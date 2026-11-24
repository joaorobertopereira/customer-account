package br.com.helpcsistemas.customeraccount.verification.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedProviderCallbackPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSessionRegistryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSessionRegistryPort.HostedSessionReference;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderCallbackInboxPort;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class ProviderCallbackControllerTest {

    private static final ProviderId PROVIDER = ProviderId.of("legitimuz");
    private static final byte[] BODY = "{\"sessionId\":\"sid-1\"}".getBytes();

    private final HostedProviderCallbackPort provider = mock(HostedProviderCallbackPort.class);
    private final HostedSessionRegistryPort sessions = mock(HostedSessionRegistryPort.class);
    private final ProviderCallbackInboxPort inbox = mock(ProviderCallbackInboxPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC);
    private final ProviderCallbackController controller =
            new ProviderCallbackController(List.of(provider), sessions, inbox, clock);

    @Test
    void authenticatesWithTheTenantRecordedOnTheSession() {
        var result = mock(HostedResult.class);
        when(provider.id()).thenReturn(PROVIDER);
        when(provider.parseCallback(BODY)).thenReturn(result);
        when(result.sessionId()).thenReturn("sid-1");
        when(result.providerEventId()).thenReturn("evt-1");
        var customerId = UUID.randomUUID();
        when(sessions.findByProviderSession(PROVIDER, "sid-1"))
                .thenReturn(Optional.of(new HostedSessionReference(
                        TenantId.of("tenant-a"),
                        customerId,
                        VerificationType.IDENTITY,
                        VerificationId.newId(),
                        PROVIDER,
                        "sid-1",
                        clock.instant().plusSeconds(600),
                        true)));
        when(provider.authenticateCallback(anyMap(), eq(BODY), eq("tenant-a"))).thenReturn(true);

        var response = controller.receive(PROVIDER.value(), new HttpHeaders(), BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(inbox).enqueue(PROVIDER, "evt-1", customerId, BODY, clock.instant());
    }

    @Test
    void rejectedSignatureIsNotEnqueued() {
        var result = mock(HostedResult.class);
        when(provider.id()).thenReturn(PROVIDER);
        when(provider.parseCallback(BODY)).thenReturn(result);
        when(result.sessionId()).thenReturn("unknown");
        when(sessions.findByProviderSession(PROVIDER, "unknown")).thenReturn(Optional.empty());
        when(provider.authenticateCallback(anyMap(), any(), eq("unmatched-provider-session")))
                .thenReturn(false);

        var response = controller.receive(PROVIDER.value(), new HttpHeaders(), BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(inbox);
    }
}
