package br.com.helpcsistemas.customeraccount.shared.domain;

/** Escrita concorrente no mesmo agregado (controle otimista de versão). O cliente pode repetir a operação. */
public class ConcurrencyConflictException extends DomainException {

    public ConcurrencyConflictException(String code, String message) {
        super(code, message);
    }
}
