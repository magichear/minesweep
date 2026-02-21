package com.magichear.minesweepBackend.service;

import com.magichear.minesweepBackend.config.properties.SolverProperties;
import com.magichear.minesweepBackend.dto.AiTestStatusVO;
import com.magichear.minesweepBackend.dto.AiTestVO;
import com.magichear.minesweepBackend.entity.AiTestRecord;
import com.magichear.minesweepBackend.model.Difficulty;
import com.magichear.minesweepBackend.repository.AiTestRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolverTestServiceTest {

    @Mock
    private AiTestRecordRepository repository;

    private SolverTestService solverTestService;

    @BeforeEach
    void setUp() {
        SolverProperties props = new SolverProperties();
        props.setMaxEnumCells(80);
        props.setMaxValidAssignments(200_000);
        SolverAssistService assistService = new SolverAssistService(props);
        solverTestService = new SolverTestService(props, assistService, repository);
    }

    // ---- startTest ----

    @Test
    void startTest_easy_returnsRunningStatus() {
        AiTestStatusVO status = solverTestService.startTest("test1", "EASY", null, "user1");

        assertNotNull(status.getTrackingId());
        assertEquals("RUNNING", status.getStatus());
        assertEquals(0, status.getProgress());
        assertEquals(100, status.getTotal());
    }

    @Test
    void startTest_medium_returnsRunningStatus() {
        AiTestStatusVO status = solverTestService.startTest("test2", "MEDIUM", null, "user1");

        assertNotNull(status.getTrackingId());
        assertEquals("RUNNING", status.getStatus());
    }

    @Test
    void startTest_hard_supported() {
        AiTestStatusVO status = solverTestService.startTest("test3", "HARD", null, "user1");

        assertNotNull(status.getTrackingId());
        assertEquals("RUNNING", status.getStatus());
    }

    @Test
    void startTest_withGameRule() {
        AiTestStatusVO status = solverTestService.startTest("test4", "EASY", "SINGLE_CELL_SAFE", "user1");

        assertNotNull(status.getTrackingId());
        assertEquals("RUNNING", status.getStatus());
    }

    @Test
    void startTest_invalidDifficulty_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> solverTestService.startTest("test1", "INVALID", null, "user1"));
    }

    // ---- getStatus ----

    @Test
    void getStatus_afterStart_returnsRunning() {
        AiTestStatusVO startStatus = solverTestService.startTest("test1", "EASY", null, "user1");
        String trackingId = startStatus.getTrackingId();

        AiTestStatusVO status = solverTestService.getStatus(trackingId);
        assertEquals(trackingId, status.getTrackingId());
        assertTrue(
            "RUNNING".equals(status.getStatus()) || "COMPLETED".equals(status.getStatus()),
            "Status should be RUNNING or COMPLETED due to async execution race"
        );
    }

    @Test
    void getStatus_unknownId_throws() {
        assertThrows(NoSuchElementException.class,
                () -> solverTestService.getStatus("non-existent-id"));
    }

    // ---- getAllTests ----

    @Test
    void getAllTests_empty() {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        List<AiTestVO> result = solverTestService.getAllTests();
        assertTrue(result.isEmpty());
    }

    @Test
    void getAllTests_returnsMappedRecords() {
        AiTestRecord record = new AiTestRecord();
        record.setId(1L);
        record.setTestName("solver test");
        record.setModelName("ExpertSolver");
        record.setDifficulty(Difficulty.EASY);
        record.setTotalGames(100);
        record.setWins(90);
        record.setWinRate(0.90);
        record.setAvgDurationMs(15L);
        record.setMaxDurationMs(50L);
        record.setMinDurationMs(5L);
        record.setUsername("tester");
        record.setCreatedAt(LocalDateTime.of(2026, 2, 20, 14, 0));

        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(record));

        List<AiTestVO> result = solverTestService.getAllTests();
        assertEquals(1, result.size());

        AiTestVO vo = result.getFirst();
        assertEquals(1L, vo.getId());
        assertEquals("solver test", vo.getTestName());
        assertEquals("ExpertSolver", vo.getModelName());
        assertEquals("EASY", vo.getDifficulty());
        assertEquals(100, vo.getTotalGames());
        assertEquals(90, vo.getWins());
        assertEquals(0.90, vo.getWinRate(), 0.001);
    }

    @Test
    void getAllTests_hardDifficultyRecords() {
        AiTestRecord record = new AiTestRecord();
        record.setId(3L);
        record.setTestName("hard test");
        record.setModelName("ExpertSolver");
        record.setDifficulty(Difficulty.HARD);
        record.setTotalGames(100);
        record.setWins(40);
        record.setWinRate(0.40);
        record.setAvgDurationMs(150L);
        record.setMaxDurationMs(500L);
        record.setMinDurationMs(20L);
        record.setUsername("tester");
        record.setCreatedAt(LocalDateTime.of(2026, 3, 1, 10, 0));

        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(record));

        List<AiTestVO> result = solverTestService.getAllTests();
        assertEquals(1, result.size());
        assertEquals("HARD", result.getFirst().getDifficulty());
    }
}
