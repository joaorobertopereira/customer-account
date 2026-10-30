package br.com.helpcsistemas.customeraccount.tenant.application.port.out;

import java.net.URI;
import java.util.Map;

public interface WebhookSenderPort {

    /**
     * @return status HTTP da resposta do tenant
     * @throws WebhookSendException falha de rede/timeout (sem resposta HTTP)
     */
    int send(URI url, Map<String, String> headers, String body);

    class WebhookSendException extends RuntimeException {

        private final String errorCode;

        public WebhookSendException(String errorCode, Throwable cause) {
            super("Falha ao enviar webhook: " + errorCode, cause);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
