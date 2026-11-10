package br.com.helpcsistemas.customeraccount.eventsourcing;

import java.time.Instant;

/**
 * Estado materializado de um agregado em uma revisão do stream. {@code data} já vem selado (cifrado, quando contém
 * dados pessoais); {@code subject} é o titular (pseudônimo) cuja chave sela o conteúdo, ou nulo.
 */
public record Snapshot(String stream, long revision, int schemaVersion, String subject, byte[] data, Instant takenAt) {}
