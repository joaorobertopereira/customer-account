package br.com.helpcsistemas.customeraccount.verification.application.port.out;

/** Falha técnica do provedor. {@code errorCode} é estável e sem dados pessoais (vai para eventos e para o tenant). */
public class ProviderUnavailableException extends RuntimeException {

    private final String errorCode;
    private final long latencyMs;

    public ProviderUnavailableException(String errorCode, long latencyMs, Throwable cause) {
        super("Provedor indisponível: " + errorCode, cause);
        this.errorCode = errorCode;
        this.latencyMs = latencyMs;
    }

    public String errorCode() {
        return errorCode;
    }

    public long latencyMs() {
        return latencyMs;
    }
}
