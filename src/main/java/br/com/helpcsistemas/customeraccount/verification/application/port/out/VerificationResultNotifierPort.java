package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;

public interface VerificationResultNotifierPort {

    /** Enfileira a notificação do resultado padronizado para o tenant (sem efeito se o tenant não tem webhook). */
    void notifyResult(VerificationView result);
}
