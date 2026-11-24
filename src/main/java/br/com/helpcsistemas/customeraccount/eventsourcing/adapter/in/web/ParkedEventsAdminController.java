package br.com.helpcsistemas.customeraccount.eventsourcing.adapter.in.web;

import br.com.helpcsistemas.customeraccount.eventsourcing.ParkedEvents;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operação da plataforma (role {@code admin}): inspeciona, reprocessa ou descarta eventos estacionados. */
@RestController
class ParkedEventsAdminController {

    private static final String BASE = "/api/v1/admin/parked-events";

    private final ParkedEvents parked;

    ParkedEventsAdminController(ParkedEvents parked) {
        this.parked = parked;
    }

    @GetMapping(BASE)
    List<ParkedEvents.Parked> list() {
        return parked.list();
    }

    @PostMapping(BASE + "/{id}/reprocess")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reprocess(@PathVariable String id) {
        parked.reprocess(id);
    }

    @DeleteMapping(BASE + "/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discard(@PathVariable String id) {
        parked.discard(id);
    }
}
