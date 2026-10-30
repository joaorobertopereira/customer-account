package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Propaga o contexto de trace (W3C {@code traceparent}) pelos metadados dos eventos: o span da subscription que
 * consome o evento vira filho do span da requisição que o gravou, mantendo um único traceId ponta a ponta.
 */
@Component
class TraceContextPropagation {

    private final Tracer tracer;
    private final Propagator propagator;

    TraceContextPropagation(Tracer tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
    }

    Map<String, String> inject(Map<String, String> metadata) {
        var context = tracer.currentTraceContext().context();
        if (context == null) {
            return metadata;
        }
        var carrier = new HashMap<>(metadata);
        propagator.inject(context, carrier, Map::put);
        return carrier;
    }

    /** Executa o handler dentro de um span de consumo ligado ao trace de origem do evento. */
    void runInConsumerSpan(String spanName, Map<String, String> metadata, Map<String, String> tags, Runnable handler) {
        var builder = propagator.extract(metadata, Map::get).name(spanName).kind(Span.Kind.CONSUMER);
        tags.forEach(builder::tag);
        var span = builder.start();
        try (var ignored = tracer.withSpan(span)) {
            handler.run();
        } catch (RuntimeException ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }
}
