package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import java.time.LocalDate;
import java.util.UUID;

/** Dados do titular enviados ao provedor. Nunca persistidos neste módulo. */
public record VerificationSubject(
        UUID customerId, String cpf, String fullName, LocalDate birthDate, String email, String mobilePhone) {

    @Override
    public String toString() {
        return "VerificationSubject[customerId=" + customerId + "]";
    }
}
