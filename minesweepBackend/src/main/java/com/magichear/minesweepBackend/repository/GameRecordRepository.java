package com.magichear.minesweepBackend.repository;

import com.magichear.minesweepBackend.entity.GameRecord;
import com.magichear.minesweepBackend.model.Difficulty;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {

    List<GameRecord> findTop3ByDifficultyAndWonTrueOrderByDurationSecondsAsc(Difficulty difficulty);

    long countByDifficulty(Difficulty difficulty);

    long countByDifficultyAndWonTrue(Difficulty difficulty);

    List<GameRecord> findByDifficultyOrderByPlayedAtAsc(Difficulty difficulty);

    long countByWonTrue();

    @Query("SELECT MIN(g.durationSeconds) FROM GameRecord g WHERE g.difficulty = :d AND g.durationSeconds IS NOT NULL")
    Optional<Long> findMinDurationByDifficulty(@Param("d") Difficulty d);

    @Query("SELECT MAX(g.durationSeconds) FROM GameRecord g WHERE g.difficulty = :d AND g.durationSeconds IS NOT NULL")
    Optional<Long> findMaxDurationByDifficulty(@Param("d") Difficulty d);
}
