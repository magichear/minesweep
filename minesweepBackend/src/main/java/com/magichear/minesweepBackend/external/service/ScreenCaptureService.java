package com.magichear.minesweepBackend.external.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for capturing screen regions, with support for debug-mode logging.
 * Hides the mouse cursor from captures by moving it out of the region temporarily.
 */
@Slf4j
@Service
public class ScreenCaptureService {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final Path debugDir;

    public ScreenCaptureService() {
        this.debugDir = Path.of("debug_captures");
    }

    /**
     * Capture a screen region. Moves the mouse cursor to the bottom-right
     * corner of the screen before capturing to avoid cursor interference.
     *
     * @param x      region left
     * @param y      region top
     * @param width  region width
     * @param height region height
     * @param debugMode if true, saves the capture to disk
     * @return captured image
     */
    public BufferedImage captureRegion(int x, int y, int width, int height, boolean debugMode) throws AWTException {
        Robot robot = new Robot();

        // Save original mouse position
        Point originalPos = MouseInfo.getPointerInfo().getLocation();

        // Move mouse out of the capture region to avoid interference
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int safeX = Math.max(0, Math.min(screenSize.width - 1, x + width + 50));
        int safeY = Math.max(0, Math.min(screenSize.height - 1, y + height + 50));
        // If the safe position is still inside capture region, move to screen corner
        if (safeX >= x && safeX <= x + width && safeY >= y && safeY <= y + height) {
            safeX = screenSize.width - 1;
            safeY = screenSize.height - 1;
        }
        robot.mouseMove(safeX, safeY);

        // Small delay to let cursor move
        robot.delay(50);

        // Capture
        Rectangle rect = new Rectangle(x, y, width, height);
        BufferedImage capture = robot.createScreenCapture(rect);

        // Restore mouse position
        robot.mouseMove(originalPos.x, originalPos.y);

        // Debug: save to disk
        if (debugMode) {
            saveDebugCapture(capture, "capture");
        }

        return capture;
    }

    /**
     * Save an image to the debug directory with timestamp.
     */
    public void saveDebugCapture(BufferedImage image, String prefix) {
        try {
            Files.createDirectories(debugDir);
            String filename = prefix + "_" + TIMESTAMP_FMT.format(LocalDateTime.now()) + ".png";
            File outFile = debugDir.resolve(filename).toFile();
            ImageIO.write(image, "png", outFile);
            log.debug("Debug capture saved: {}", outFile.getAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to save debug capture", e);
        }
    }

    /**
     * Save text log to the debug directory.
     */
    public void saveDebugLog(String content, String prefix) {
        try {
            Files.createDirectories(debugDir);
            String filename = prefix + "_" + TIMESTAMP_FMT.format(LocalDateTime.now()) + ".txt";
            Path logFile = debugDir.resolve(filename);
            Files.writeString(logFile, content);
            log.debug("Debug log saved: {}", logFile.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to save debug log", e);
        }
    }

    /**
     * Perform a mouse click at screen coordinates.
     */
    public void clickAt(int screenX, int screenY, boolean rightClick) throws AWTException {
        Robot robot = new Robot();
        robot.mouseMove(screenX, screenY);
        robot.delay(30);
        int button = rightClick
                ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK
                : java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        robot.mousePress(button);
        robot.delay(20);
        robot.mouseRelease(button);
        robot.delay(50);
    }
}
