package br.com.helpcsistemas.customeraccount.eventsourcing.adapter.in.web;

import br.com.helpcsistemas.customeraccount.eventsourcing.SubscriptionAdmin;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operação da plataforma (role {@code admin}): lista subscriptions e reconstrói projeções. */
@RestController
class SubscriptionsAdminController {

    private static final String BASE = "/api/v1/admin/subscriptions";

    private final SubscriptionAdmin subscriptions;

    SubscriptionsAdminController(SubscriptionAdmin subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping(BASE)
    List<SubscriptionAdmin.Info> list() {
        return subscriptions.list();
    }

    @PostMapping(BASE + "/{name}/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void rebuild(@PathVariable String name) {
        subscriptions.rebuild(name);
    }
}
