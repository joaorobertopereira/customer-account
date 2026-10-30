package br.com.helpcsistemas.customeraccount.shared.domain;

/** Valor de entrada inválido (formato, tamanho, dígito verificador). A mensagem nunca deve conter o valor. */
public class InvalidValueException extends DomainException {

    public InvalidValueException(String code, String message) {
        super(code, message);
    }
}
