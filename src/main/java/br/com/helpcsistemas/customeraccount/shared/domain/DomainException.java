package br.com.helpcsistemas.customeraccount.shared.domain;

/** Base de todas as exceções de domínio. Carrega um código estável para clientes da API. */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
