package br.com.helpcsistemas.customeraccount.shared.domain;

import java.util.Objects;

/** Quem executou a operação (usuário ou client OAuth2), para trilha de auditoria. Vem sempre do token. */
public record Actor(String id) {

    public Actor {
        Objects.requireNonNull(id, "actor");
    }

    @Override
    public String toString() {
        return id;
    }
}
