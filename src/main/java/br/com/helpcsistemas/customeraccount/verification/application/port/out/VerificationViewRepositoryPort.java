package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationViewRepositoryPort {

    Optional<VerificationView> findById(VerificationId id);

    List<VerificationView> findByTenantAndCustomer(TenantId tenant, UUID customerId);

    void save(VerificationView view);

    void deleteByCustomer(UUID customerId);

    /** Validações aguardando confirmação cujo prazo já venceu (candidatas à expiração). */
    List<VerificationId> findAwaitingExpiredBefore(Instant instant, int limit);

    List<VerificationId> findValidityExpiredBefore(Instant instant, int limit);
}
