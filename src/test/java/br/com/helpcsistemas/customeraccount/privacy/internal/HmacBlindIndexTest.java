package br.com.helpcsistemas.customeraccount.privacy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HmacBlindIndexTest {

    private final HmacBlindIndex index = new HmacBlindIndex("ZGV2LWJsaW5kLWluZGV4LWtleS1ub3QtZm9yLXByb2Q=");

    @Test
    void isDeterministicAndDoesNotLeakTheValue() {
        var first = index.compute("cpf", "52998224725");

        assertThat(index.compute("cpf", "52998224725")).isEqualTo(first);
        assertThat(first).doesNotContain("52998224725").matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void separatesPurposes() {
        assertThat(index.compute("cpf", "123")).isNotEqualTo(index.compute("email", "123"));
    }

    @Test
    void rejectsWeakKeys() {
        assertThatThrownBy(() -> new HmacBlindIndex("c2hvcnQ=")).isInstanceOf(IllegalStateException.class);
    }
}
