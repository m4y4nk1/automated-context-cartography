package com.vw.eacontext.graph;

/**
 * Builds a direction-agnostic key for a pair of applications.
 *
 * <p>Relationships, interfaces and information flows are captured independently
 * in the source data and are not guaranteed to agree on which side is the
 * dependent one — the same coupling is routinely recorded as
 * {@code A depends on B} and {@code B provides an interface to A}. Anything that
 * needs to ask "are these two applications connected at all?" must therefore
 * compare pairs without regard to direction.</p>
 */
public final class ApplicationPairKey {

    private ApplicationPairKey() {
    }

    /**
     * @param a one application id
     * @param b the other application id
     * @return a key that is identical for {@code (a, b)} and {@code (b, a)}
     */
    public static String of(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }
}
