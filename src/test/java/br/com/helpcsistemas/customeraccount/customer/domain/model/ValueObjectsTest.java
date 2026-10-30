package br.com.helpcsistemas.customeraccount.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectsTest {

    @Test
    void normalizesAndMasksCpf() {
        var cpf = Cpf.of("529.982.247-25");

        assertThat(cpf.value()).isEqualTo("52998224725");
        assertThat(cpf.toString()).isEqualTo("***.982.247-**").doesNotContain("52998224725");
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224724", "11111111111", "123", "abcdefghijk", ""})
    void rejectsInvalidCpf(String raw) {
        assertThatThrownBy(() -> Cpf.of(raw)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void normalizesAndMasksEmail() {
        var email = Email.of("  Maria.Souza@Example.COM ");

        assertThat(email.value()).isEqualTo("maria.souza@example.com");
        assertThat(email.toString()).isEqualTo("m***@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sem-arroba", "a@b", "a@@b.com", "a b@c.com"})
    void rejectsInvalidEmail(String raw) {
        assertThatThrownBy(() -> Email.of(raw)).isInstanceOf(InvalidValueException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"(11) 91234-5678", "11912345678", "+55 11 91234-5678", "5511912345678"})
    void normalizesMobilePhoneToE164(String raw) {
        var phone = MobilePhone.of(raw);

        assertThat(phone.value()).isEqualTo("+5511912345678");
        assertThat(phone.toString()).isEqualTo("+5511*****5678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1132345678", "(11) 81234-5678", "+1 202 555 0100"})
    void rejectsNonBrazilianMobile(String raw) {
        assertThatThrownBy(() -> MobilePhone.of(raw)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void personNameNormalizesSpacesAndMasks() {
        var name = PersonName.of("  Maria   da  Silva ");

        assertThat(name.value()).isEqualTo("Maria da Silva");
        assertThat(name.hasSurname()).isTrue();
        assertThat(name.toString()).isEqualTo("Maria ***");
    }

    @Test
    void addressNormalizesZipCodeAndHidesStreetInToString() {
        var address =
                new Address("01310-100", " Av. Paulista ", "1000", " ", "Bela Vista", "São Paulo", BrazilianState.SP);

        assertThat(address.zipCode()).isEqualTo("01310100");
        assertThat(address.complement()).isNull();
        assertThat(address.toString()).doesNotContain("Paulista");
    }

    @Test
    void rejectsUnknownState() {
        assertThatThrownBy(() -> BrazilianState.of("XX")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void requiresFullName() {
        assertThatThrownBy(() -> new PersonalData(
                        PersonName.of("Maria"),
                        null,
                        java.time.LocalDate.of(1990, 1, 1),
                        null,
                        null,
                        CountryCode.BRAZIL))
                .isInstanceOf(InvalidValueException.class);
    }
}
