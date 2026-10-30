package br.com.helpcsistemas.customeraccount.customer.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Dados de identificação civil. Nome social (Decreto 8.727/2016) é opcional e, quando presente, deve ser
 * priorizado na exibição. Dados sensíveis (art. 5º, II LGPD — biometria, saúde, etnia, religião) NÃO ficam aqui.
 */
public record PersonalData(
        PersonName fullName,
        PersonName socialName,
        LocalDate birthDate,
        PersonName motherName,
        MaritalStatus maritalStatus,
        CountryCode nationality) {

    /** Menores exigem consentimento específico dos responsáveis (LGPD art. 14) — fora do escopo deste core. */
    public static final int MINIMUM_AGE = 18;

    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    public PersonalData {
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(birthDate, "birthDate");
        Objects.requireNonNull(nationality, "nationality");
        if (!fullName.hasSurname()) {
            throw new InvalidValueException("INCOMPLETE_FULL_NAME", "Informe o nome completo");
        }
    }

    public void ensureEligibleOn(LocalDate today) {
        if (birthDate.isAfter(today) || birthDate.isBefore(MIN_BIRTH_DATE)) {
            throw new InvalidValueException("INVALID_BIRTH_DATE", "Data de nascimento inválida");
        }
        if (Period.between(birthDate, today).getYears() < MINIMUM_AGE) {
            throw new BusinessRuleException(
                    "CUSTOMER_UNDERAGE", "Cadastro permitido apenas para maiores de " + MINIMUM_AGE + " anos");
        }
    }

    @Override
    public String toString() {
        return "PersonalData[***]";
    }
}
