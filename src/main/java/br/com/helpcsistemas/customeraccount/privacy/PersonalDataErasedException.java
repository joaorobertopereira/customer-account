package br.com.helpcsistemas.customeraccount.privacy;

/** Os dados do titular foram eliminados (chave destruída) e não podem mais ser lidos. */
public class PersonalDataErasedException extends RuntimeException {

    public PersonalDataErasedException(String message) {
        super(message);
    }
}
