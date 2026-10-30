package br.com.helpcsistemas.customeraccount.tenant.application.port.in;

public interface DispatchNotificationsUseCase {

    /** @return quantidade de notificações processadas nesta execução */
    int dispatchDue(int batchSize);
}
