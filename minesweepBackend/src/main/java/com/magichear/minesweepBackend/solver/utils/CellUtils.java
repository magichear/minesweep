package com.magichear.minesweepBackend.solver.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Static utility methods for cell-key encoding, neighbour enumeration,
 * and revealed / unrevealed neighbour counting.
 * <p>
 * Cell key = {@code row * width + col} (stored as {@code long}).
 */
public final class CellUtils {

    private CellUtils() {}

    /** Encode (row, col) into a single {@code long} key. */
    public static long key(int r, int c, int w) {
        return (long) r * w + c;
    }

    /** Decode row from a cell key. */
    public static int keyRow(long k, int w) {
        return (int) (k / w);
    }

    /** Decode column from a cell key. */
    public static int keyCol(long k, int w) {
        return (int) (k % w);
    }

    /**
     * Return the (up-to-8) neighbours of the given cell within the board bounds.
     *
     * @return list of {@code long[]{row, col}} pairs
     */
    public static List<long[]> getNeighbors(int r, int c, int h, int w) {
        List<long[]> result = new ArrayList<>(8);
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < h && nc >= 0 && nc < w) {
                    result.add(new long[]{nr, nc});
                }
            }
        }
        return result;
    }

    /** Count neighbours of {@code cellKey} that are revealed (value 0-8). */
    public static int countRevealedNeighbors(int[][] view, long cellKey, int h, int w) {
        int cnt = 0;
        for (long[] nb : getNeighbors(keyRow(cellKey, w), keyCol(cellKey, w), h, w)) {
            int val = view[(int) nb[0]][(int) nb[1]];
            if (val >= 0 && val <= 8) cnt++;
        }
        return cnt;
    }

    /** Count neighbours of {@code cellKey} that are unrevealed (value == 9). */
    public static int countUnrevealedNeighbors(int[][] view, long cellKey, int h, int w) {
        int cnt = 0;
        for (long[] nb : getNeighbors(keyRow(cellKey, w), keyCol(cellKey, w), h, w)) {
            if (view[(int) nb[0]][(int) nb[1]] == 9) cnt++;
        }
        return cnt;
    }
}
