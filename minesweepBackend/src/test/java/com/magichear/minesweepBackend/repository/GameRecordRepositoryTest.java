package com.magichear.minesweepBackend.repository;

import com.magichear.minesweepBackend.entity.GameRecord;
import com.magichear.minesweepBackend.model.Difficulty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class GameRecordRepositoryTest {

    @Autowired
    private GameRecordRepository repository;

    @Test
    void saveAndCount() {
        repository.save(makeRecord(Difficulty.EASY, true, 30L));
        repository.save(makeRecord(Difficulty.EASY, false, null));
        repository.save(makeRecord(Difficulty.MEDIUM, true, 60L));

        assertEquals(3, repository.count());
        assertEquals(2, repository.countByWonTrue());
        assertEquals(2, repository.countByDifficulty(Difficulty.EASY));
        assertEquals(1, repository.countByDifficultyAndWonTrue(Difficulty.EASY));
    }

    @Test
    void top3FastestWins() {
        repository.save(makeRecord(Difficulty.EASY, true, 50L));
        repository.save(makeRecord(Difficulty.EASY, true, 20L));
        repository.save(makeRecord(Difficulty.EASY, true, 35L));
        repository.save(makeRecord(Difficulty.EASY, true, 10L));

        List<GameRecord> top3 = repository
                .findTop3ByDifficultyAndWonTrueOrderByDurationSecondsAsc(Difficulty.EASY);

        assertEquals(3, top3.size());
        assertEquals(10L, top3.get(0).getDurationSeconds());
        assertEquals(20L, top3.get(1).getDurationSeconds());
        assertEquals(35L, top3.get(2).getDurationSeconds());
    }

    @Test
    void orderedByPlayedAt() {
        GameRecord r1 = makeRecord(Difficulty.EASY, true, 10L);
        r1.setPlayedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        GameRecord r2 = makeRecord(Difficulty.EASY, false, null);
        r2.setPlayedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        repository.save(r1);
        repository.save(r2);

        List<GameRecord> records = repository.findByDifficultyOrderByPlayedAtAsc(Difficulty.EASY);
        assertEquals(2, records.size());
        assertTrue(records.get(0).isWon());
        assertFalse(records.get(1).isWon());
    }

    private GameRecord makeRecord(Difficulty d, boolean won, Long duration) {
        GameRecord r = new GameRecord();
        r.setDifficulty(d);
        r.setWon(won);
        r.setDurationSeconds(duration);
        r.setPlayedAt(LocalDateTime.now());
        return r;
    }
}
