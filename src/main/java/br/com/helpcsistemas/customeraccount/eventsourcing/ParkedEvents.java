package br.com.helpcsistemas.customeraccount.eventsourcing;

import java.time.Instant;
import java.util.List;

/**
 * Eventos que uma subscription não conseguiu processar após várias tentativas (eventos "envenenados"). Ficam
 * estacionados para não travar a projeção; depois de corrigida a causa, podem ser reprocessados ou descartados.
 */
public interface ParkedEvents {

    List<Parked> list();

    /** Reexecuta o subscriber original com o evento estacionado; remove-o em caso de sucesso. */
    void reprocess(String id);

    /** Descarta o evento (decisão consciente de não projetá-lo). */
    void discard(String id);

    /** Visão administrativa: sem o payload do evento (pode conter dados pessoais cifrados). */
    record Parked(
            String id,
            String subscription,
            String stream,
            long revision,
            String eventId,
            String eventType,
            String error,
            int attempts,
            Instant parkedAt) {}
}
