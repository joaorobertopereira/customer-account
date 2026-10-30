package br.com.helpcsistemas.customeraccount.shared.domain;

public class ResourceAlreadyExistsException extends DomainException {

    public ResourceAlreadyExistsException(String code, String message) {
        super(code, message);
    }
}
