package br.com.helpcsistemas.customeraccount.shared.domain;

import java.security.SecureRandom;
import java.util.UUID;

/** Gera UUID v7 (RFC 9562): ordenável por tempo (bom para índices) e imprevisível (dificulta enumeração/IDOR). */
public final class Identifiers {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Identifiers() {}

    public static UUID newUuidV7() {
        long timestamp = System.currentTimeMillis();
        long msb = (timestamp << 16) | (0x7L << 12) | (RANDOM.nextInt() & 0x0FFFL);
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
