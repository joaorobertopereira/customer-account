package br.com.helpcsistemas.customeraccount.shared.domain;

public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
