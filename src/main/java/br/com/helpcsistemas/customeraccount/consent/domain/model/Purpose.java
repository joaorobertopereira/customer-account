package br.com.helpcsistemas.customeraccount.consent.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Set;

public record Purpose(
        String code, String description, LegalBasis legalBasis, Set<String> fields, String policyVersion) {
    public static final Set<String> ALL_FIELDS =
            br.com.helpcsistemas.customeraccount.shared.domain.PersonalDataFields.ALL;

    public Purpose {
        if (code == null
                || !code.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")
                || description == null
                || description.isBlank()
                || description.length() > 2000
                || legalBasis == null
                || fields == null
                || fields.isEmpty()
                || !ALL_FIELDS.containsAll(fields)
                || policyVersion == null
                || policyVersion.isBlank()
                || policyVersion.length() > 100) {
            throw new InvalidValueException("INVALID_PURPOSE", "Finalidade ou campos inválidos");
        }
        fields = Set.copyOf(fields);
    }

    public enum LegalBasis {
        CONSENT,
        CONTRACT,
        LEGAL_OBLIGATION,
        LEGITIMATE_INTEREST,
        CREDIT_PROTECTION
    }
}
