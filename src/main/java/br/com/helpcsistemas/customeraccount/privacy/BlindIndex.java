package br.com.helpcsistemas.customeraccount.privacy;

/**
 * Índice cego: HMAC-SHA256 determinístico de um valor normalizado. Permite unicidade e busca exata (CPF, e-mail)
 * sem guardar o valor em claro. O {@code purpose} separa domínios (o mesmo valor gera índices distintos).
 */
public interface BlindIndex {

    String compute(String purpose, String normalizedValue);
}
