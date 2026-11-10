package br.com.helpcsistemas.customeraccount.eventsourcing;

import java.util.List;

/** Operação das subscriptions desta instância: inspeção e reconstrução de projeções. */
public interface SubscriptionAdmin {

    List<Info> list();

    /**
     * Reconstrói uma projeção: para a subscription, descarta o estado derivado, apaga o checkpoint e reprocessa todo o
     * histórico. A reconstrução roda em segundo plano; durante ela as consultas à projeção ficam incompletas.
     *
     * <p>Projeções que dependem de outras (consentimento e validações consultam o vínculo no read model de clientes)
     * devem ser reconstruídas depois da de clientes.
     */
    void rebuild(String name);

    record Info(String name, String streamPrefix, boolean rebuildable, boolean running) {}
}
