package br.com.helpcsistemas.customeraccount.shared.domain;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }
}
