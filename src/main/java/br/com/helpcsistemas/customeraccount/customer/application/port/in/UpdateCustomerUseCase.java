package br.com.helpcsistemas.customeraccount.customer.application.port.in;

import br.com.helpcsistemas.customeraccount.customer.domain.model.Address;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonalData;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;

public interface UpdateCustomerUseCase {

    void updatePersonalData(TenantId tenant, CustomerId id, PersonalData personalData);

    void changeAddress(TenantId tenant, CustomerId id, Address address);

    void changeEmail(TenantId tenant, CustomerId id, Email email);

    void changeMobilePhone(TenantId tenant, CustomerId id, MobilePhone mobilePhone);
}
