package com.magichear.minesweepBackend.controller;

import com.magichear.minesweepBackend.dto.DifficultyStatsVO;
import com.magichear.minesweepBackend.dto.StatsVO;
import com.magichear.minesweepBackend.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatsService statsService;

    @Test
    void getStats_ok() throws Exception {
        StatsVO stats = new StatsVO();
        stats.setGlobalTotalGames(10);
        stats.setGlobalWinRate(0.6);

        DifficultyStatsVO ds = new DifficultyStatsVO();
        ds.setTotalGames(10);
        ds.setTotalWins(6);
        ds.setWinRate(0.6);
        ds.setMaxConsecutiveWins(3);
        ds.setTopRecords(List.of());

        stats.setEasy(ds);
        stats.setMedium(new DifficultyStatsVO());
        stats.setHard(new DifficultyStatsVO());

        when(statsService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalTotalGames").value(10))
                .andExpect(jsonPath("$.globalWinRate").value(0.6))
                .andExpect(jsonPath("$.easy.maxConsecutiveWins").value(3));
    }
}
