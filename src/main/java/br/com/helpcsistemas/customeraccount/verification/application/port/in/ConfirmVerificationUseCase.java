package br.com.helpcsistemas.customeraccount.verification.application.port.in;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;

public interface ConfirmVerificationUseCase {

    /** Envia a resposta do titular (ex.: código OTP) ao provedor que emitiu o desafio. */
    VerificationView confirm(TenantId tenant, VerificationId id, String answer);
}
