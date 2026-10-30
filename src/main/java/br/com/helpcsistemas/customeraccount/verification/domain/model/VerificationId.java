package br.com.helpcsistemas.customeraccount.verification.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.Identifiers;
import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Objects;
import java.util.UUID;

public record VerificationId(UUID value) {

    public VerificationId {
        Objects.requireNonNull(value, "verificationId");
    }

    public static VerificationId newId() {
        return new VerificationId(Identifiers.newUuidV7());
    }

    public static VerificationId of(String raw) {
        try {
            return new VerificationId(UUID.fromString(raw));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidValueException("INVALID_VERIFICATION_ID", "Identificador de validação inválido");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
