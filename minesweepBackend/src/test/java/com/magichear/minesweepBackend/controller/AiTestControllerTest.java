package com.magichear.minesweepBackend.controller;

import com.magichear.minesweepBackend.dto.AiTestStatusVO;
import com.magichear.minesweepBackend.dto.AiTestVO;
import com.magichear.minesweepBackend.service.SolverTestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiTestController.class)
class AiTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SolverTestService solverTestService;

    // ---- start ----

    @Test
    void startTest_ok() throws Exception {
        AiTestStatusVO status = new AiTestStatusVO();
        status.setTrackingId("track-123");
        status.setStatus("RUNNING");
        status.setProgress(0);
        status.setTotal(100);

        // username comes from request attribute set by JWT filter; null in @WebMvcTest
        when(solverTestService.startTest(eq("my test"), eq("EASY"), eq(null), eq(null)))
                .thenReturn(status);

        mockMvc.perform(post("/api/ai-test/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testName\":\"my test\",\"difficulty\":\"EASY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("track-123"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.total").value(100));
    }

    @Test
    void startTest_medium_ok() throws Exception {
        AiTestStatusVO status = new AiTestStatusVO();
        status.setTrackingId("track-456");
        status.setStatus("RUNNING");
        status.setProgress(0);
        status.setTotal(100);

        when(solverTestService.startTest(eq("test"), eq("MEDIUM"), eq(null), eq(null)))
                .thenReturn(status);

        mockMvc.perform(post("/api/ai-test/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testName\":\"test\",\"difficulty\":\"MEDIUM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("track-456"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void startTest_hard_ok() throws Exception {
        AiTestStatusVO status = new AiTestStatusVO();
        status.setTrackingId("track-789");
        status.setStatus("RUNNING");
        status.setProgress(0);
        status.setTotal(100);

        when(solverTestService.startTest(eq("test"), eq("HARD"), eq(null), eq(null)))
                .thenReturn(status);

        mockMvc.perform(post("/api/ai-test/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testName\":\"test\",\"difficulty\":\"HARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("track-789"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    // ---- status ----

    @Test
    void getStatus_ok() throws Exception {
        AiTestStatusVO status = new AiTestStatusVO();
        status.setTrackingId("track-123");
        status.setStatus("COMPLETED");
        status.setProgress(100);
        status.setTotal(100);

        when(solverTestService.getStatus("track-123")).thenReturn(status);

        mockMvc.perform(get("/api/ai-test/status/track-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress").value(100));
    }

    @Test
    void getStatus_notFound_returns404() throws Exception {
        when(solverTestService.getStatus("nope"))
                .thenThrow(new NoSuchElementException("Test not found: nope"));

        mockMvc.perform(get("/api/ai-test/status/nope"))
                .andExpect(status().isNotFound());
    }

    // ---- getAll ----

    @Test
    void getAllTests_ok() throws Exception {
        AiTestVO vo = new AiTestVO();
        vo.setId(1L);
        vo.setTestName("batch-1");
        vo.setModelName("model");
        vo.setDifficulty("EASY");
        vo.setTotalGames(100);
        vo.setWins(60);
        vo.setWinRate(0.6);
        vo.setUsername("user");
        vo.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));

        when(solverTestService.getAllTests()).thenReturn(List.of(vo));

        mockMvc.perform(get("/api/ai-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].testName").value("batch-1"))
                .andExpect(jsonPath("$[0].wins").value(60));
    }

    @Test
    void getAllTests_empty() throws Exception {
        when(solverTestService.getAllTests()).thenReturn(List.of());

        mockMvc.perform(get("/api/ai-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
