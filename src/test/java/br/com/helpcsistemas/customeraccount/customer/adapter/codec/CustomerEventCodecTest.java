package br.com.helpcsistemas.customeraccount.customer.adapter.codec;

import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.customer.CustomerFixtures;
import br.com.helpcsistemas.customeraccount.customer.domain.event.AddressChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerErased;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerEvent;
import br.com.helpcsistemas.customeraccount.customer.domain.event.EmailChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.MobilePhoneChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.PersonalDataUpdated;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.eventsourcing.NewEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CustomerEventCodecTest {

    private final FakeCipher cipher = new FakeCipher();
    private final CustomerEventCodec codec =
            new CustomerEventCodec(JsonMapper.builder().build(), cipher);
    private final CustomerId id = CustomerId.newId();

    @Test
    void roundTripsEveryEventType() {
        var at = CLOCK.instant();
        var events = new java.util.ArrayList<CustomerEvent>(
                CustomerFixtures.newCustomer(id).pendingEvents());
        events.addAll(List.of(
                new PersonalDataUpdated(id, TENANT_A, CustomerFixtures.personalData(), at),
                new AddressChanged(id, TENANT_A, CustomerFixtures.address(), at),
                new EmailChanged(id, TENANT_A, Email.of("nova@example.com"), at),
                new MobilePhoneChanged(id, TENANT_A, MobilePhone.of("21987654321"), at),
                new CustomerErased(id, TENANT_A, at)));

        for (var event : events) {
            assertThat(codec.decode(stored(codec.encode(event)))).isEqualTo(event);
        }
    }

    @Test
    void encryptsEventsWithPersonalData() {
        var registered =
                codec.encode(CustomerFixtures.newCustomer(id).pendingEvents().getFirst());

        assertThat(registered.json()).isFalse();
        assertThat(registered.metadata()).containsEntry("encrypted", "true");
        assertThat(new String(registered.data(), StandardCharsets.UTF_8))
                .doesNotContain(CustomerFixtures.CPF)
                .doesNotContain("maria.souza@example.com");
    }

    @Test
    void erasureEventIsPlainJsonWithoutPersonalData() {
        var erased = codec.encode(new CustomerErased(id, TENANT_A, CLOCK.instant()));

        assertThat(erased.json()).isTrue();
        assertThat(erased.metadata()).containsEntry("encrypted", "false");
    }

    @Test
    void decodingFailsAfterCryptoShredding() {
        var registered = stored(
                codec.encode(CustomerFixtures.newCustomer(id).pendingEvents().getFirst()));
        cipher.erased.add(id.toString());

        assertThatThrownBy(() -> codec.decode(registered)).isInstanceOf(PersonalDataErasedException.class);
    }

    private StoredEvent stored(NewEvent event) {
        return new StoredEvent(
                codec.streamOf(id), 0, event.eventId(), event.type(), event.data(), event.metadata(), CLOCK.instant());
    }

    /** Cifra reversível de teste (inverte bytes) que simula o shredding por titular. */
    static final class FakeCipher implements PersonalDataCipher {

        final Set<String> erased = new HashSet<>();

        @Override
        public byte[] encrypt(String subjectId, byte[] plaintext) {
            return reverse(plaintext);
        }

        @Override
        public byte[] decrypt(String subjectId, byte[] ciphertext) {
            if (erased.contains(subjectId)) {
                throw new PersonalDataErasedException("eliminado");
            }
            return reverse(ciphertext);
        }

        private static byte[] reverse(byte[] input) {
            var output = new byte[input.length];
            for (int i = 0; i < input.length; i++) {
                output[i] = input[input.length - 1 - i];
            }
            return output;
        }
    }
}
