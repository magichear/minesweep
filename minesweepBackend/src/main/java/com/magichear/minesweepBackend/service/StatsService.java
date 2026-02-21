package com.magichear.minesweepBackend.service;

import com.magichear.minesweepBackend.dto.*;
import com.magichear.minesweepBackend.entity.GameRecord;
import com.magichear.minesweepBackend.model.Difficulty;
import com.magichear.minesweepBackend.repository.GameRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class StatsService {

    private final GameRecordRepository repository;

    public StatsService(GameRecordRepository repository) {
        this.repository = repository;
    }

    public StatsVO getStats() {
        StatsVO stats = new StatsVO();
        stats.setEasy(getDifficultyStats(Difficulty.EASY));
        stats.setMedium(getDifficultyStats(Difficulty.MEDIUM));
        stats.setHard(getDifficultyStats(Difficulty.HARD));

        long totalGames = repository.count();
        long totalWins = repository.countByWonTrue();
        stats.setGlobalTotalGames((int) totalGames);
        stats.setGlobalWinRate(totalGames > 0 ? (double) totalWins / totalGames : 0.0);

        log.info("Stats retrieved \u2013 totalGames={}, globalWinRate={}",
                totalGames, String.format("%.2f", stats.getGlobalWinRate()));
        return stats;
    }

    private DifficultyStatsVO getDifficultyStats(Difficulty difficulty) {
        DifficultyStatsVO stats = new DifficultyStatsVO();

        long total = repository.countByDifficulty(difficulty);
        long wins = repository.countByDifficultyAndWonTrue(difficulty);

        stats.setTotalGames((int) total);
        stats.setTotalWins((int) wins);
        stats.setWinRate(total > 0 ? (double) wins / total : 0.0);

        // max consecutive wins
        List<GameRecord> records = repository.findByDifficultyOrderByPlayedAtAsc(difficulty);
        stats.setMaxConsecutiveWins(computeMaxConsecutiveWins(records));

        // min/max durations across all finished games
        stats.setMinDurationSeconds(repository.findMinDurationByDifficulty(difficulty).orElse(null));
        stats.setMaxDurationSeconds(repository.findMaxDurationByDifficulty(difficulty).orElse(null));

        // top-3 fastest wins
        List<GameRecord> topWins =
                repository.findTop3ByDifficultyAndWonTrueOrderByDurationSecondsAsc(difficulty);
        stats.setTopRecords(topWins.stream().map(r -> {
            TopRecordVO vo = new TopRecordVO();
            vo.setDurationSeconds(r.getDurationSeconds());
            vo.setPlayedAt(r.getPlayedAt());
            return vo;
        }).toList());

        return stats;
    }

    private int computeMaxConsecutiveWins(List<GameRecord> records) {
        int max = 0;
        int current = 0;
        for (GameRecord r : records) {
            if (r.isWon()) {
                current++;
                if (current > max) max = current;
            } else {
                current = 0;
            }
        }
        return max;
    }
}
