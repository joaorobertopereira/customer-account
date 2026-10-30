package br.com.helpcsistemas.customeraccount.eventsourcing;

/** Estado esperado do stream ao gravar (concorrência otimista). */
public sealed interface ExpectedRevision {

    record Any() implements ExpectedRevision {}

    record NoStream() implements ExpectedRevision {}

    record Exact(long revision) implements ExpectedRevision {}

    static ExpectedRevision any() {
        return new Any();
    }

    static ExpectedRevision noStream() {
        return new NoStream();
    }

    /** Versão negativa = agregado novo (stream ainda não existe). */
    static ExpectedRevision of(long version) {
        return version < 0 ? new NoStream() : new Exact(version);
    }
}
