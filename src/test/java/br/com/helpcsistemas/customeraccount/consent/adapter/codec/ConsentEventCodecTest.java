package br.com.helpcsistemas.customeraccount.consent.adapter.codec;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent;
import br.com.helpcsistemas.customeraccount.consent.domain.model.ConsentEvidence;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.*;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ConsentEventCodecTest {
    @Test
    void versionedEventsRoundTripAndEvidenceAlwaysUsesSubjectEncryption() {
        var cipher = mock(PersonalDataCipher.class);
        var codec = new ConsentEventCodec(JsonMapper.builder().build(), cipher);
        var id = UUID.randomUUID();
        when(cipher.encrypt(eq(id.toString()), any())).thenAnswer(inv -> reverse(inv.getArgument(1)));
        when(cipher.decrypt(eq(id.toString()), any())).thenAnswer(inv -> reverse(inv.getArgument(1)));
        var now = Instant.parse("2026-01-15T12:00:00Z");
        for (var event : List.<ConsentEvent>of(
                new ConsentEvent.ConsentGranted(
                        id,
                        TenantId.of("tenant-a"),
                        "email",
                        "1",
                        new ConsentEvidence("web", "session", "a".repeat(64)),
                        now,
                        now),
                new ConsentEvent.ConsentRevoked(id, TenantId.of("tenant-a"), "email", "Holder requested", now))) {
            var encoded = codec.encode(event);
            assertThat(encoded.json()).isFalse();
            var stored = new StoredEvent(
                    "consent-" + id + "-tenant-a",
                    0,
                    encoded.eventId(),
                    encoded.type(),
                    encoded.data(),
                    encoded.metadata(),
                    now);
            assertThat(codec.decode(stored)).isEqualTo(event);
            when(cipher.decrypt(eq(id.toString()), any())).thenThrow(new PersonalDataErasedException("Erased"));
            assertThatThrownBy(() -> codec.decode(stored)).isInstanceOf(PersonalDataErasedException.class);
            doAnswer(inv -> reverse(inv.getArgument(1))).when(cipher).decrypt(eq(id.toString()), any());
        }
    }

    private static byte[] reverse(byte[] bytes) {
        var result = bytes.clone();
        for (int i = 0; i < result.length; i++) {
            result[i] = bytes[bytes.length - 1 - i];
        }
        return result;
    }
}
