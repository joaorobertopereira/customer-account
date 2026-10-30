package br.com.helpcsistemas.customeraccount.customer.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Objects;

/** CPF normalizado (11 dígitos) com dígitos verificadores válidos. {@link #toString()} sempre mascara. */
public record Cpf(String value) {

    public Cpf {
        Objects.requireNonNull(value, "cpf");
        if (!isValid(value)) {
            throw new InvalidValueException("INVALID_CPF", "CPF inválido");
        }
    }

    public static Cpf of(String raw) {
        if (raw == null) {
            throw new InvalidValueException("INVALID_CPF", "CPF inválido");
        }
        return new Cpf(raw.replaceAll("[.\\-\\s]", ""));
    }

    public String masked() {
        return "***." + value.substring(3, 6) + "." + value.substring(6, 9) + "-**";
    }

    @Override
    public String toString() {
        return masked();
    }

    private static boolean isValid(String digits) {
        if (!digits.matches("\\d{11}") || digits.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(digits, 9) == digits.charAt(9) - '0' && checkDigit(digits, 10) == digits.charAt(10) - '0';
    }

    private static int checkDigit(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int result = (sum * 10) % 11;
        return result == 10 ? 0 : result;
    }
}
