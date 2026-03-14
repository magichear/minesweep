package com.magichear.minesweepBackend.external.recognition;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Recognizes minesweeper board state from a screenshot image.
 * <p>
 * Detects grid lines, infers cell boundaries, and classifies each cell
 * as unrevealed (9), revealed number (0-8), mine (-1), or flag (-3).
 * Supports arbitrary board sizes (9x9, 16x16, 16x30, etc.).
 * <p>
 * The algorithm:
 * <ol>
 *   <li>Detect horizontal and vertical grid separator lines by scanning for
 *       low-brightness bands (grey ≤ SEPARATOR_THRESHOLD) or mid-grey borders.</li>
 *   <li>Infer cell regions from the gaps between separator lines.</li>
 *   <li>For each cell, sample a grid of pixels in the central area.</li>
 *   <li>Classify based on dominant colors:
 *       unrevealed (has 82/61 gradient), revealed-0 (uniform ~48),
 *       or number 1-8 (detected by specific text colors).</li>
 * </ol>
 */
public class BoardRecognizer {

    /** Result of board recognition. */
    public record RecognitionResult(
            int rows,
            int cols,
            int[][] board,
            List<String> warnings,
            CellRegion[][] cellRegions
    ) {}

    /** Pixel region for a single cell. */
    public record CellRegion(int xStart, int yStart, int xEnd, int yEnd) {
        public int centerX() { return (xStart + xEnd) / 2; }
        public int centerY() { return (yStart + yEnd) / 2; }
        public int width()   { return xEnd - xStart; }
        public int height()  { return yEnd - yStart; }
    }

    // --- Color profiles for number recognition ---
    // Each number has a characteristic RGB color in the minesweeper UI.
    private static final int[][] NUMBER_COLORS = {
            // {R, G, B} for numbers 1-8
            {61, 74, 186},   // 1: blue
            {24, 102, 16},   // 2: green
            {130, 31, 31},   // 3: red
            {124, 64, 179},  // 4: purple
            {227, 66, 52},   // 5: dark red / orange-red
            {0, 131, 143},   // 6: teal
            {66, 66, 66},    // 7: dark grey (hard to distinguish, rarely seen)
            {158, 158, 158}, // 8: light grey (extremely rare)
    };

    private static final int COLOR_DISTANCE_THRESHOLD = 60;
    private static final int UNREVEALED_BRIGHT_GREY = 82;
    private static final int UNREVEALED_MID_GREY = 61;
    private static final int REVEALED_BG_GREY = 48;
    private static final int SEPARATOR_GREY_MIN = 49;
    private static final int SEPARATOR_GREY_MAX = 55;

    // Mine cell: bright red background
    private static final int MINE_RED_MIN = 190;

    // Flag: check for flag emoji colors or red marker
    private static final int FLAG_RED_MIN = 200;
    private static final int FLAG_GREEN_MAX = 80;

    public RecognitionResult recognize(BufferedImage image) {
        List<String> warnings = new ArrayList<>();

        // 1. Detect grid lines
        List<int[]> hBands = detectSeparatorBands(image, true);
        List<int[]> vBands = detectSeparatorBands(image, false);

        // 2. Compute cell regions
        List<int[]> colRegions = computeCellRegions(hBands, image.getWidth());
        List<int[]> rowRegions = computeCellRegions(vBands, image.getHeight());

        int rows = rowRegions.size();
        int cols = colRegions.size();

        if (rows == 0 || cols == 0) {
            warnings.add("Could not detect grid structure. rows=" + rows + " cols=" + cols);
            return new RecognitionResult(0, 0, new int[0][0], warnings, new CellRegion[0][0]);
        }

        // Validate cell sizes are roughly uniform
        validateCellSizes(rowRegions, colRegions, warnings);

        // 3. Build cell regions and classify
        CellRegion[][] regions = new CellRegion[rows][cols];
        int[][] board = new int[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                regions[r][c] = new CellRegion(
                        colRegions.get(c)[0], rowRegions.get(r)[0],
                        colRegions.get(c)[1], rowRegions.get(r)[1]
                );
                board[r][c] = classifyCell(image, regions[r][c]);
            }
        }

        // 4. Validation: check for game-over state
        boolean hasGameOverText = detectGameOverText(image, warnings);
        if (hasGameOverText) {
            warnings.add("Game-over text detected in screenshot.");
        }

        return new RecognitionResult(rows, cols, board, warnings, regions);
    }

    public RecognitionResult recognize(File imageFile) throws IOException {
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) throw new IOException("Failed to read image: " + imageFile);
        return recognize(image);
    }

    public RecognitionResult recognize(InputStream is) throws IOException {
        BufferedImage image = ImageIO.read(is);
        if (image == null) throw new IOException("Failed to read image from stream");
        return recognize(image);
    }

    // ========== Grid Detection ==========

    /**
     * Detect separator bands (dark/grey lines between cells).
     * Scans multiple lines for robustness and clusters detected positions.
     *
     * @param horizontal true to detect vertical separators (scan horizontally),
     *                   false to detect horizontal separators (scan vertically)
     */
    List<int[]> detectSeparatorBands(BufferedImage image, boolean horizontal) {
        int scanLength = horizontal ? image.getWidth() : image.getHeight();
        int perpLength = horizontal ? image.getHeight() : image.getWidth();

        // Sample several scan lines in the middle third of the image
        int[] scanPositions = new int[5];
        for (int i = 0; i < 5; i++) {
            scanPositions[i] = perpLength / 3 + (perpLength / 3) * i / 5;
        }

        // Count how often each position is part of a separator
        int[] separatorVotes = new int[scanLength];
        for (int scanPos : scanPositions) {
            for (int pos = 0; pos < scanLength; pos++) {
                int rgb = horizontal
                        ? image.getRGB(pos, scanPos)
                        : image.getRGB(scanPos, pos);
                int grey = greyValue(rgb);
                boolean isSep = isSeparatorGrey(grey);
                if (isSep) {
                    separatorVotes[pos]++;
                }
            }
        }

        // Find bands where majority of scan lines agree
        List<int[]> bands = new ArrayList<>();
        int threshold = 3; // need at least 3 of 5 scan lines to agree
        int bandStart = -1;
        for (int pos = 0; pos < scanLength; pos++) {
            if (separatorVotes[pos] >= threshold) {
                if (bandStart == -1) bandStart = pos;
            } else {
                if (bandStart != -1) {
                    bands.add(new int[]{bandStart, pos - 1});
                    bandStart = -1;
                }
            }
        }
        if (bandStart != -1) {
            bands.add(new int[]{bandStart, scanLength - 1});
        }

        // Filter out very narrow bands (< 3px) which are noise
        bands.removeIf(b -> (b[1] - b[0] + 1) < 3);

        // Filter out bands that are too wide (> 15px) — these might be cell content
        bands.removeIf(b -> (b[1] - b[0] + 1) > 15);

        return bands;
    }

    private boolean isSeparatorGrey(int grey) {
        return grey >= SEPARATOR_GREY_MIN && grey <= SEPARATOR_GREY_MAX;
    }

    /**
     * Given separator bands, compute the cell regions between them.
     */
    List<int[]> computeCellRegions(List<int[]> bands, int totalLength) {
        List<int[]> regions = new ArrayList<>();

        if (bands.isEmpty()) {
            // No separators detected — treat entire image as one cell (shouldn't happen)
            regions.add(new int[]{0, totalLength - 1});
            return regions;
        }

        // Region before first band
        if (bands.getFirst()[0] > 5) {
            regions.add(new int[]{0, bands.getFirst()[0] - 1});
        }

        // Regions between consecutive bands
        for (int i = 0; i < bands.size() - 1; i++) {
            int start = bands.get(i)[1] + 1;
            int end = bands.get(i + 1)[0] - 1;
            if (end > start + 2) { // at least 3px wide
                regions.add(new int[]{start, end});
            }
        }

        // Region after last band
        if (bands.getLast()[1] < totalLength - 6) {
            int start = bands.getLast()[1] + 1;
            int end = totalLength - 1;
            if (end - start > 5) {
                regions.add(new int[]{start, end});
            }
        }

        // Post-process: remove outlier regions that are much smaller than the median
        if (regions.size() > 2) {
            int[] widths = regions.stream().mapToInt(r -> r[1] - r[0]).sorted().toArray();
            int median = widths[widths.length / 2];
            // Remove regions that are less than 40% of median (likely margin fragments)
            regions.removeIf(r -> (r[1] - r[0]) < median * 0.4);
        }

        return regions;
    }

    // ========== Cell Classification ==========

    /**
     * Classify a single cell based on pixel color analysis.
     *
     * @return 0-8 for revealed number, 9 for unrevealed, -1 for mine, -3 for flag
     */
    int classifyCell(BufferedImage image, CellRegion region) {
        int w = region.width();
        int h = region.height();

        // Sample the inner portion of the cell (skip border pixels)
        int marginX = Math.max(2, w / 6);
        int marginY = Math.max(2, h / 6);
        int innerXStart = region.xStart + marginX;
        int innerXEnd = region.xEnd - marginX;
        int innerYStart = region.yStart + marginY;
        int innerYEnd = region.yEnd - marginY;

        // Collect color statistics
        int totalPixels = 0;
        int revealedBgCount = 0;     // grey ~48
        int unrevealedCount = 0;     // grey 61 or 82
        int[] numberVotes = new int[8]; // votes for numbers 1-8
        int coloredPixelCount = 0;
        int mineRedCount = 0;
        int flagRedCount = 0;

        int sampleStep = Math.max(1, Math.min(w, h) / 20);
        if (sampleStep < 1) sampleStep = 1;

        for (int y = innerYStart; y <= innerYEnd; y += sampleStep) {
            for (int x = innerXStart; x <= innerXEnd; x += sampleStep) {
                if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) continue;
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                totalPixels++;

                // Check if it's a grey (R≈G≈B)
                if (isGrey(r, g, b)) {
                    int grey = (r + g + b) / 3;
                    if (Math.abs(grey - REVEALED_BG_GREY) <= 5) {
                        revealedBgCount++;
                    } else if (Math.abs(grey - UNREVEALED_MID_GREY) <= 5
                            || Math.abs(grey - UNREVEALED_BRIGHT_GREY) <= 5) {
                        unrevealedCount++;
                    }
                    continue;
                }

                // Non-grey pixel — check for number colors
                coloredPixelCount++;

                // Match against number colors
                int bestNum = -1;
                int bestDist = Integer.MAX_VALUE;
                for (int n = 0; n < NUMBER_COLORS.length; n++) {
                    int dr = r - NUMBER_COLORS[n][0];
                    int dg = g - NUMBER_COLORS[n][1];
                    int db = b - NUMBER_COLORS[n][2];
                    int dist = dr * dr + dg * dg + db * db;
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestNum = n;
                    }
                }
                if (bestDist < COLOR_DISTANCE_THRESHOLD * COLOR_DISTANCE_THRESHOLD) {
                    numberVotes[bestNum]++;
                }

                // Check mine (bright red background) and other markers.
                // Keep this after number vote collection so number "5" pixels are not lost.
                if (r > MINE_RED_MIN && g < 95 && b < 95 && (r - g) > 70 && (r - b) > 70) {
                    mineRedCount++;
                }

                if (r > FLAG_RED_MIN && g < FLAG_GREEN_MAX && b < FLAG_GREEN_MAX) {
                    flagRedCount++;
                }
            }
        }

        if (totalPixels == 0) return 9; // unrevealed fallback

        // Decision logic
        double coloredRatio = (double) coloredPixelCount / totalPixels;
        double unrevealedRatio = (double) unrevealedCount / totalPixels;
        double revealedRatio = (double) revealedBgCount / totalPixels;

        // Mine detection: mine cells are dominated by bright red background.
        // A single number "5" should not satisfy this condition.
        if (mineRedCount > totalPixels * 0.35) {
            return -1;
        }

        // Flag detection: significant red marker pixels on an otherwise unrevealed cell
        if (flagRedCount > 3 && unrevealedRatio > 0.2) {
            return -3;
        }

        // Unrevealed: dominated by 61/82 grey, no significant colored pixels
        if (unrevealedRatio > 0.3 && coloredRatio < 0.05) {
            return 9;
        }

        // Check for number votes
        int bestNumber = -1;
        int bestVotes = 0;
        for (int n = 0; n < numberVotes.length; n++) {
            if (numberVotes[n] > bestVotes) {
                bestVotes = numberVotes[n];
                bestNumber = n;
            }
        }

        // If we have significant number-color pixels, classify as that number
        if (bestVotes >= 2) {
            return bestNumber + 1; // 0-indexed to 1-indexed
        }

        // Revealed empty (0): mostly 48-grey background, no colored pixels
        if (revealedRatio > 0.3 && coloredRatio < 0.03) {
            return 0;
        }

        // Fallback: if more unrevealed-like, return 9, else 0
        if (unrevealedRatio > revealedRatio) {
            return 9;
        }
        return 0;
    }

    // ========== Utilities ==========

    private boolean isGrey(int r, int g, int b) {
        return Math.abs(r - g) <= 8 && Math.abs(g - b) <= 8 && Math.abs(r - b) <= 8;
    }

    private int greyValue(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r + g + b) / 3;
    }

    /**
     * Simple heuristic to detect game-over text overlay (e.g., "you" in yellow).
     */
    private boolean detectGameOverText(BufferedImage image, List<String> warnings) {
        int yellowCount = 0;
        int scanStep = 4;
        for (int y = 0; y < image.getHeight(); y += scanStep) {
            for (int x = 0; x < image.getWidth(); x += scanStep) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r > 200 && g > 140 && b < 80) {
                    yellowCount++;
                }
            }
        }
        return yellowCount > 50;
    }

    private void validateCellSizes(List<int[]> rowRegions, List<int[]> colRegions, List<String> warnings) {
        int[] rowHeights = rowRegions.stream().mapToInt(r -> r[1] - r[0]).toArray();
        int[] colWidths = colRegions.stream().mapToInt(r -> r[1] - r[0]).toArray();

        double avgH = Arrays.stream(rowHeights).average().orElse(0);
        double avgW = Arrays.stream(colWidths).average().orElse(0);

        for (int i = 0; i < rowHeights.length; i++) {
            if (Math.abs(rowHeights[i] - avgH) > avgH * 0.3) {
                warnings.add("Row " + i + " height (" + rowHeights[i] + "px) deviates significantly from average (" + String.format("%.0f", avgH) + "px)");
            }
        }
        for (int i = 0; i < colWidths.length; i++) {
            if (Math.abs(colWidths[i] - avgW) > avgW * 0.3) {
                warnings.add("Col " + i + " width (" + colWidths[i] + "px) deviates significantly from average (" + String.format("%.0f", avgW) + "px)");
            }
        }
    }

    /**
     * Convert recognition result board to AI-format (0-8 revealed, 9 unrevealed).
     * Mine (-1) and flag (-3) cells are treated as unrevealed (9) for the solver.
     */
    public static int[][] toAiBoard(int[][] board) {
        int rows = board.length;
        int cols = board[0].length;
        int[][] aiBoard = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c] >= 0 && board[r][c] <= 8) {
                    aiBoard[r][c] = board[r][c];
                } else {
                    aiBoard[r][c] = 9; // unrevealed / mine / flag → treat as unrevealed
                }
            }
        }
        return aiBoard;
    }

    /**
     * Format board as human-readable string for debugging.
     */
    public static String boardToString(int[][] board) {
        StringBuilder sb = new StringBuilder();
        for (int[] row : board) {
            for (int j = 0; j < row.length; j++) {
                if (j > 0) sb.append(' ');
                switch (row[j]) {
                    case 9  -> sb.append('.');
                    case -1 -> sb.append('*');
                    case -3 -> sb.append('F');
                    default -> sb.append(row[j]);
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
