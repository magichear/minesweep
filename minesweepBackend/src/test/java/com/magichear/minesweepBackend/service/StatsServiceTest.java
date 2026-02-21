package com.magichear.minesweepBackend.service;

import com.magichear.minesweepBackend.dto.DifficultyStatsVO;
import com.magichear.minesweepBackend.dto.StatsVO;
import com.magichear.minesweepBackend.entity.GameRecord;
import com.magichear.minesweepBackend.model.Difficulty;
import com.magichear.minesweepBackend.repository.GameRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private GameRecordRepository repository;

    private StatsService statsService;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(repository);
    }

    @Test
    void getStats_empty() {
        when(repository.count()).thenReturn(0L);
        when(repository.countByWonTrue()).thenReturn(0L);
        when(repository.countByDifficulty(any())).thenReturn(0L);
        when(repository.countByDifficultyAndWonTrue(any())).thenReturn(0L);
        when(repository.findByDifficultyOrderByPlayedAtAsc(any())).thenReturn(List.of());
        when(repository.findTop3ByDifficultyAndWonTrueOrderByDurationSecondsAsc(any()))
                .thenReturn(List.of());

        StatsVO stats = statsService.getStats();
        assertEquals(0, stats.getGlobalTotalGames());
        assertEquals(0.0, stats.getGlobalWinRate());
        assertNotNull(stats.getEasy());
        assertNotNull(stats.getMedium());
        assertNotNull(stats.getHard());
    }

    @Test
    void maxConsecutiveWins_computed() {
        // W W L W W W → max streak = 3
        List<GameRecord> records = makeRecords(true, true, false, true, true, true);
        stubForDifficulty(Difficulty.EASY, 6, 5, records);
        stubOtherDifficulties(Difficulty.EASY);
        when(repository.count()).thenReturn(6L);
        when(repository.countByWonTrue()).thenReturn(5L);

        StatsVO stats = statsService.getStats();
        assertEquals(3, stats.getEasy().getMaxConsecutiveWins());
    }

    @Test
    void maxConsecutiveWins_allWins() {
        List<GameRecord> records = makeRecords(true, true, true, true);
        stubForDifficulty(Difficulty.EASY, 4, 4, records);
        stubOtherDifficulties(Difficulty.EASY);
        when(repository.count()).thenReturn(4L);
        when(repository.countByWonTrue()).thenReturn(4L);

        StatsVO stats = statsService.getStats();
        assertEquals(4, stats.getEasy().getMaxConsecutiveWins());
    }

    @Test
    void maxConsecutiveWins_allLosses() {
        List<GameRecord> records = makeRecords(false, false, false);
        stubForDifficulty(Difficulty.EASY, 3, 0, records);
        stubOtherDifficulties(Difficulty.EASY);
        when(repository.count()).thenReturn(3L);
        when(repository.countByWonTrue()).thenReturn(0L);

        StatsVO stats = statsService.getStats();
        assertEquals(0, stats.getEasy().getMaxConsecutiveWins());
    }

    @Test
    void winRate_calculated() {
        stubForDifficulty(Difficulty.EASY, 10, 7, List.of());
        stubOtherDifficulties(Difficulty.EASY);
        when(repository.count()).thenReturn(10L);
        when(repository.countByWonTrue()).thenReturn(7L);

        StatsVO stats = statsService.getStats();
        assertEquals(0.7, stats.getGlobalWinRate(), 0.001);
        assertEquals(0.7, stats.getEasy().getWinRate(), 0.001);
    }

    @Test
    void topRecords_mapped() {
        GameRecord r = new GameRecord();
        r.setDifficulty(Difficulty.EASY);
        r.setWon(true);
        r.setDurationSeconds(42L);
        r.setPlayedAt(LocalDateTime.of(2026, 1, 1, 12, 0));

        stubForDifficulty(Difficulty.EASY, 1, 1, List.of(r));
        when(repository.findTop3ByDifficultyAndWonTrueOrderByDurationSecondsAsc(Difficulty.EASY))
                .thenReturn(List.of(r));
        stubOtherDifficulties(Difficulty.EASY);
        when(repository.count()).thenReturn(1L);
        when(repository.countByWonTrue()).thenReturn(1L);

        StatsVO stats = statsService.getStats();
        DifficultyStatsVO easy = stats.getEasy();
        assertEquals(1, easy.getTopRecords().size());
        assertEquals(42L, easy.getTopRecords().getFirst().getDurationSeconds());
    }

    // ---- helpers ----

    private List<GameRecord> makeRecords(boolean... results) {
        List<GameRecord> list = new ArrayList<>();
        for (boolean won : results) {
            GameRecord r = new GameRecord();
            r.setWon(won);
            r.setDifficulty(Difficulty.EASY);
            r.setDurationSeconds(won ? 30L : null);
            r.setPlayedAt(LocalDateTime.now());
            list.add(r);
        }
        return list;
    }

    private void stubForDifficulty(Difficulty d, long total, long wins, List<GameRecord> records) {
        when(repository.countByDifficulty(d)).thenReturn(total);
        when(repository.countByDifficultyAndWonTrue(d)).thenReturn(wins);
        when(repository.findByDifficultyOrderByPlayedAtAsc(d)).thenReturn(records);
        when(repository.findTop3ByDifficultyAndWonTrueOrderByDurationSecondsAsc(d))
                .thenReturn(List.of());
    }

    private void stubOtherDifficulties(Difficulty except) {
        for (Difficulty d : Difficulty.values()) {
            if (d == except) continue;
            when(repository.countByDifficulty(d)).thenReturn(0L);
            when(repository.countByDifficultyAndWonTrue(d)).thenReturn(0L);
            when(repository.findByDifficultyOrderByPlayedAtAsc(d)).thenReturn(List.of());
            when(repository.findTop3ByDifficultyAndWonTrueOrderByDurationSecondsAsc(d))
                    .thenReturn(List.of());
        }
    }
}
