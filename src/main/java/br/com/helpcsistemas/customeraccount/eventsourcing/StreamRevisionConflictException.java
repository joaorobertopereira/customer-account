package br.com.helpcsistemas.customeraccount.eventsourcing;

/** O stream foi alterado por outra escrita entre a leitura e a gravação. */
public class StreamRevisionConflictException extends RuntimeException {

    public StreamRevisionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
