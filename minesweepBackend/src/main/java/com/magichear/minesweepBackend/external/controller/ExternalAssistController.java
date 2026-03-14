package com.magichear.minesweepBackend.external.controller;

import com.magichear.minesweepBackend.external.dto.AnalyzeResponse;
import com.magichear.minesweepBackend.external.dto.AutoClickRequest;
import com.magichear.minesweepBackend.external.service.ExternalAssistService;
import com.magichear.minesweepBackend.external.service.ScreenCaptureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * REST API for external minesweeper assistance.
 * All endpoints are under /api/external.
 */
@Slf4j
@RestController
@RequestMapping("/api/external")
public class ExternalAssistController {

    private final ExternalAssistService assistService;
    private final ScreenCaptureService captureService;

    public ExternalAssistController(ExternalAssistService assistService,
                                    ScreenCaptureService captureService) {
        this.assistService = assistService;
        this.captureService = captureService;
    }

    /**
     * Analyze a screen region: capture → recognize → solve.
     */
    @PostMapping("/analyze-region")
    public ResponseEntity<AnalyzeResponse> analyzeRegion(@RequestBody Map<String, Object> body) {
        try {
            int x = ((Number) body.get("x")).intValue();
            int y = ((Number) body.get("y")).intValue();
            int width = ((Number) body.get("width")).intValue();
            int height = ((Number) body.get("height")).intValue();
            int rows = ((Number) body.get("rows")).intValue();
            int cols = ((Number) body.get("cols")).intValue();
            int mines = ((Number) body.get("mines")).intValue();
            boolean debug = Boolean.TRUE.equals(body.get("debugMode"));

            AnalyzeResponse response = assistService.analyzeRegion(
                    x, y, width, height, rows, cols, mines, debug);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error analyzing region", e);
            return ResponseEntity.internalServerError().body(
                    new AnalyzeResponse(0, 0, new int[0][0], new double[0][0],
                            -1, -1, "error", List.of(e.getMessage()), false));
        }
    }

    /**
     * Analyze an uploaded image: recognize → solve.
     */
    @PostMapping(value = "/analyze-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalyzeResponse> analyzeImage(
            @RequestParam("image") MultipartFile file,
            @RequestParam("rows") int rows,
            @RequestParam("cols") int cols,
            @RequestParam("mines") int mines,
            @RequestParam(value = "debugMode", required = false, defaultValue = "false") boolean debugMode
    ) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                return ResponseEntity.badRequest().body(
                        new AnalyzeResponse(0, 0, new int[0][0], new double[0][0],
                                -1, -1, "error", List.of("Invalid image file"), false));
            }
            AnalyzeResponse response = assistService.analyzeImage(image, rows, cols, mines, debugMode);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error analyzing image", e);
            return ResponseEntity.internalServerError().body(
                    new AnalyzeResponse(0, 0, new int[0][0], new double[0][0],
                            -1, -1, "error", List.of(e.getMessage()), false));
        }
    }

    /**
     * Perform a mouse click at screen coordinates (for auto-play).
     */
    @PostMapping("/click")
    public ResponseEntity<Map<String, String>> autoClick(@RequestBody AutoClickRequest req) {
        try {
            captureService.clickAt(req.screenX(), req.screenY(), req.rightClick());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            log.error("Error performing click", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
