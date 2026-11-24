package br.com.helpcsistemas.customeraccount.verification.adapter.in.scheduler;

import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.ReceiveProviderResultUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedProviderCallbackPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderCallbackInboxPort;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class CallbackInboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(CallbackInboxProcessor.class);

    private final int batchSize;
    private final ProviderCallbackInboxPort inbox;
    private final Map<
                    br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId,
                    HostedProviderCallbackPort>
            providers;
    private final ReceiveProviderResultUseCase results;
    private final Clock clock;

    CallbackInboxProcessor(
            ProviderCallbackInboxPort inbox,
            List<HostedProviderCallbackPort> providers,
            ReceiveProviderResultUseCase results,
            Clock clock,
            @Value("${app.verification.callback-inbox.batch-size:50}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.verification.callback-inbox.batch-size deve ser >= 1");
        }
        this.batchSize = batchSize;
        this.inbox = inbox;
        this.providers =
                providers.stream().collect(Collectors.toMap(HostedProviderCallbackPort::id, Function.identity()));
        this.results = results;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.verification.callback-inbox.interval:2s}")
    public int processDue() {
        var callbacks = inbox.claimDue(clock.instant(), batchSize);
        callbacks.forEach(callback -> {
            var provider = providers.get(callback.provider());
            if (provider == null) {
                inbox.markUnmatched(callback.id(), clock.instant());
                return;
            }
            try {
                results.receive(callback.provider(), provider.parseCallback(callback.payload()));
                inbox.markProcessed(callback.id(), clock.instant());
            } catch (ResourceNotFoundException ex) {
                inbox.markUnmatched(callback.id(), clock.instant());
                log.atWarn()
                        .addKeyValue("provider.id", callback.provider())
                        .addKeyValue("provider.eventId", callback.eventId())
                        .log("Callback assinado não corresponde a uma sessão ativa");
            } catch (RuntimeException ex) {
                int attempts = callback.attempts() + 1;
                var delay = Duration.ofSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
                inbox.retry(callback.id(), attempts, clock.instant().plus(delay), clock.instant());
                log.atError()
                        .addKeyValue("provider.id", callback.provider())
                        .addKeyValue("provider.eventId", callback.eventId())
                        .addKeyValue("callback.attempt", attempts)
                        .setCause(ex)
                        .log("Falha ao processar callback autenticado");
            }
        });
        return callbacks.size();
    }
}
