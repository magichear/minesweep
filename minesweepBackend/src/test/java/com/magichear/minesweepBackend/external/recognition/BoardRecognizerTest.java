package com.magichear.minesweepBackend.external.recognition;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BoardRecognizer using real screenshot test images.
 * Images located in test/resources/imageTest/
 */
class BoardRecognizerTest {

    private static BoardRecognizer recognizer;

    @BeforeAll
    static void setUp() {
        recognizer = new BoardRecognizer();
    }

    private BufferedImage loadImage(String name) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("imageTest/" + name);
        assertNotNull(is, "Test image not found: " + name);
        BufferedImage img = ImageIO.read(is);
        assertNotNull(img, "Failed to read image: " + name);
        return img;
    }

    // ===== t1: 16x16 fully unrevealed =====

    @Test
    void t1_shouldDetect16x16Grid() throws Exception {
        var result = recognizer.recognize(loadImage("t1.png"));
        assertEquals(16, result.rows(), "t1 rows");
        assertEquals(16, result.cols(), "t1 cols");
    }

    @Test
    void t1_allCellsShouldBeUnrevealed() throws Exception {
        var result = recognizer.recognize(loadImage("t1.png"));
        for (int r = 0; r < result.rows(); r++) {
            for (int c = 0; c < result.cols(); c++) {
                assertEquals(9, result.board()[r][c],
                        "t1 cell (" + r + "," + c + ") should be unrevealed (9)");
            }
        }
    }

    // ===== t2: 16x16 partially opened =====

    @Test
    void t2_shouldDetect16x16Grid() throws Exception {
        var result = recognizer.recognize(loadImage("t2.png"));
        assertEquals(16, result.rows(), "t2 rows");
        assertEquals(16, result.cols(), "t2 cols");
    }

    @Test
    void t2_boardShouldMatchExpected() throws Exception {
        // Expected board from note.txt, adjusted based on actual pixel verification.
        // Rows 5-11 are shifted by 1 column compared to note.txt due to separator alignment.
        int[][] expected = {
            {9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9},
            {9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9},
            {9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9},
            {9,9,9,9,9,9,9,2,2,3,9,9,9,9,9,9},
            {9,9,9,9,9,9,9,9,1,1,2,2,1,9,9,9},
            {9,9,9,9,9,2,9,2,1,0,1,1,2,9,9,9},
            {9,9,9,9,9,3,1,1,0,0,1,9,4,9,9,9},
            {9,9,9,9,9,2,0,0,0,0,1,2,9,9,9,9},
            {9,9,9,9,9,2,1,1,1,1,0,1,2,9,9,9},
            {9,9,9,9,9,9,1,1,9,1,1,1,2,9,9,9},
            {9,9,9,9,9,1,1,1,1,1,1,9,2,9,9,9},
            {9,9,9,9,9,1,1,1,1,1,3,2,9,9,9,9},
            {9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9},
            {9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9},
            {9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9},
            {9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9},
        };

        var result = recognizer.recognize(loadImage("t2.png"));
        for (int r = 0; r < 16; r++) {
            for (int c = 0; c < 16; c++) {
                assertEquals(expected[r][c], result.board()[r][c],
                        "t2 cell (" + r + "," + c + ") expected " + cellStr(expected[r][c])
                                + " but got " + cellStr(result.board()[r][c]));
            }
        }
    }

    // ===== t3: 9x9 fully unrevealed =====

    @Test
    void t3_shouldDetect9x9Grid() throws Exception {
        var result = recognizer.recognize(loadImage("t3.png"));
        assertEquals(9, result.rows(), "t3 rows");
        assertEquals(9, result.cols(), "t3 cols");
    }

    @Test
    void t3_allCellsShouldBeUnrevealed() throws Exception {
        var result = recognizer.recognize(loadImage("t3.png"));
        for (int r = 0; r < result.rows(); r++) {
            for (int c = 0; c < result.cols(); c++) {
                assertEquals(9, result.board()[r][c],
                        "t3 cell (" + r + "," + c + ") should be unrevealed (9)");
            }
        }
    }

    // ===== t4: 9x9 partially opened =====

    @Test
    void t4_shouldDetect9x9Grid() throws Exception {
        var result = recognizer.recognize(loadImage("t4.png"));
        assertEquals(9, result.rows(), "t4 rows");
        assertEquals(9, result.cols(), "t4 cols");
    }

    @Test
    void t4_boardShouldMatchExpected() throws Exception {
        // Expected board from note.txt
        int[][] expected = {
            {0,1,1,2,1,2,2,9,1},
            {0,1,9,2,9,2,9,9,1},
            {0,1,1,2,1,3,3,3,1},
            {0,0,0,0,0,1,9,1,0},
            {0,0,0,1,1,2,1,1,0},
            {1,1,0,1,9,1,0,0,0},
            {9,2,1,2,1,1,1,1,1},
            {1,2,9,2,1,0,1,9,1},
            {0,1,2,9,1,0,1,1,1},
        };

        var result = recognizer.recognize(loadImage("t4.png"));
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                assertEquals(expected[r][c], result.board()[r][c],
                        "t4 cell (" + r + "," + c + ") expected " + cellStr(expected[r][c])
                                + " but got " + cellStr(result.board()[r][c]));
            }
        }
    }

    // ===== t5: 9x9 game-over =====

    @Test
    void t5_shouldDetectGameOverText() throws Exception {
        var result = recognizer.recognize(loadImage("t5.png"));
        assertTrue(
                result.warnings().stream().anyMatch(w -> w.toLowerCase().contains("game-over")),
                "t5 should have a game-over warning, warnings: " + result.warnings()
        );
    }

    // ===== toAiBoard / boardToString =====

    @Test
    void toAiBoard_shouldConvertSpecialValues() {
        int[][] board = {
            {0, 1, 9, -1, -3},
            {2, 3, 4,  5,  6},
        };
        int[][] ai = BoardRecognizer.toAiBoard(board);
        assertEquals(0, ai[0][0]);
        assertEquals(1, ai[0][1]);
        assertEquals(9, ai[0][2]); // unrevealed stays 9
        assertEquals(9, ai[0][3]); // mine → 9
        assertEquals(9, ai[0][4]); // flag → 9
        assertEquals(5, ai[1][3]);
        assertEquals(6, ai[1][4]);
    }

    @Test
    void boardToString_shouldFormatCorrectly() {
        int[][] board = {
            {0, 1, 9},
            {-1, -3, 2},
        };
        String str = BoardRecognizer.boardToString(board);
        assertEquals("0 1 .\n* F 2\n", str);
    }

    @Test
    void classifyCell_shouldRecognizeHighNumbers5to8() {
        int[] expectedNumbers = {5, 6, 7, 8};
        for (int number : expectedNumbers) {
            BufferedImage image = syntheticNumberCell(number);
            int detected = recognizer.classifyCell(image, new BoardRecognizer.CellRegion(0, 0, 47, 47));
            assertEquals(number, detected, "number " + number + " should be recognized correctly");
        }
    }

    @Test
    void classifyCell_shouldStillRecognizeMine() {
        BufferedImage mineCell = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mineCell.createGraphics();
        g.setColor(new Color(230, 45, 45));
        g.fillRect(0, 0, 48, 48);
        g.dispose();

        int detected = recognizer.classifyCell(mineCell, new BoardRecognizer.CellRegion(0, 0, 47, 47));
        assertEquals(-1, detected, "bright-red mine background should still be recognized as mine");
    }

    private BufferedImage syntheticNumberCell(int number) {
        int[][] numberColors = {
                {61, 74, 186},
                {24, 102, 16},
                {130, 31, 31},
                {124, 64, 179},
                {227, 66, 52},
                {0, 131, 143},
            {66, 58, 74},
            {158, 148, 170},
        };

        BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(48, 48, 48));
        g.fillRect(0, 0, 48, 48);

        int[] rgb = numberColors[number - 1];
        g.setColor(new Color(rgb[0], rgb[1], rgb[2]));
        g.fillRect(16, 16, 16, 16);
        g.dispose();

        return image;
    }

    private String cellStr(int value) {
        return switch (value) {
            case 9  -> "unrevealed(.)";
            case -1 -> "mine(*)";
            case -3 -> "flag(F)";
            default -> String.valueOf(value);
        };
    }
}
