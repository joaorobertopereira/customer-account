package br.com.helpcsistemas.customeraccount.consent.application.port.out;

import br.com.helpcsistemas.customeraccount.consent.domain.model.Purpose;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.List;
import java.util.Optional;

public interface PurposeRepository {
    void save(TenantId tenant, Purpose purpose);

    Optional<Purpose> find(TenantId tenant, String code);

    List<Purpose> list(TenantId tenant);
}
