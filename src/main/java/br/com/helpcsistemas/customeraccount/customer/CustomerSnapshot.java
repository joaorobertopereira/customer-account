package br.com.helpcsistemas.customeraccount.customer;

import java.time.LocalDate;
import java.util.UUID;

/** Dados do titular expostos a outros módulos. {@link #toString()} não revela dados pessoais. */
public record CustomerSnapshot(
        UUID id, String cpf, String fullName, LocalDate birthDate, String email, String mobilePhone) {

    @Override
    public String toString() {
        return "CustomerSnapshot[id=" + id + "]";
    }
}
