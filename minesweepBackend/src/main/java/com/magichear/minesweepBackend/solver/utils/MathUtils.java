package com.magichear.minesweepBackend.solver.utils;

import java.math.BigInteger;

/**
 * Static combinatorics and number-theory helpers used by the solver.
 */
public final class MathUtils {

    private MathUtils() {}

    /**
     * Binomial coefficient C(n, k) as {@link BigInteger}.
     * Returns {@link BigInteger#ZERO} for invalid inputs (k &lt; 0 or k &gt; n).
     */
    public static BigInteger comb(int n, int k) {
        if (k < 0 || k > n) return BigInteger.ZERO;
        if (k == 0 || k == n) return BigInteger.ONE;
        if (k > n - k) k = n - k;
        BigInteger result = BigInteger.ONE;
        for (int i = 0; i < k; i++) {
            result = result.multiply(BigInteger.valueOf(n - i)).divide(BigInteger.valueOf(i + 1));
        }
        return result;
    }

    /** Greatest common divisor (always non-negative). */
    public static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }
}
