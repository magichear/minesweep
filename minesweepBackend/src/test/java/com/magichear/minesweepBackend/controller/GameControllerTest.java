package com.magichear.minesweepBackend.controller;

import com.magichear.minesweepBackend.dto.GameStateVO;
import com.magichear.minesweepBackend.dto.PredictionVO;
import com.magichear.minesweepBackend.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GameController.class)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameService gameService;


    @Test
    void newGame_ok() throws Exception {
        GameStateVO vo = new GameStateVO();
        vo.setGameId("test-id");
        vo.setRows(9);
        vo.setCols(9);
        vo.setMines(10);
        vo.setDifficulty("EASY");
        vo.setPlayerBoard(new int[9][9]);
        vo.setFlagged(new boolean[9][9]);

        when(gameService.createGame(eq("EASY"), isNull())).thenReturn(vo);

        mockMvc.perform(post("/api/game/new")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":\"EASY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value("test-id"))
                .andExpect(jsonPath("$.rows").value(9));
    }

    @Test
    void reveal_ok() throws Exception {
        GameStateVO vo = new GameStateVO();
        vo.setGameId("g1");
        vo.setPlayerBoard(new int[9][9]);
        vo.setFlagged(new boolean[9][9]);

        when(gameService.revealCell(eq("g1"), eq(4), eq(4))).thenReturn(vo);

        mockMvc.perform(post("/api/game/g1/reveal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"row\":4,\"col\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value("g1"));
    }

    @Test
    void flag_ok() throws Exception {
        GameStateVO vo = new GameStateVO();
        vo.setGameId("g1");
        vo.setPlayerBoard(new int[9][9]);
        vo.setFlagged(new boolean[9][9]);

        when(gameService.toggleFlag(eq("g1"), eq(0), eq(0))).thenReturn(vo);

        mockMvc.perform(post("/api/game/g1/flag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"row\":0,\"col\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void predict_ok() throws Exception {
        PredictionVO pv = new PredictionVO();
        pv.setProbabilities(new double[9][9]);
        pv.setSafestRow(3);
        pv.setSafestCol(5);

        when(gameService.predict("g1")).thenReturn(pv);

        mockMvc.perform(post("/api/game/g1/predict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.safestRow").value(3))
                .andExpect(jsonPath("$.safestCol").value(5));
    }

    @Test
    void getState_notFound() throws Exception {
        when(gameService.getGameState("nope"))
                .thenThrow(new NoSuchElementException("Game not found: nope"));

        mockMvc.perform(get("/api/game/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void predict_allDifficulties_supported() throws Exception {
        PredictionVO pv = new PredictionVO();
        pv.setProbabilities(new double[16][30]);
        pv.setSafestRow(8);
        pv.setSafestCol(15);

        when(gameService.predict("g2")).thenReturn(pv);

        mockMvc.perform(post("/api/game/g2/predict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.safestRow").value(8))
                .andExpect(jsonPath("$.safestCol").value(15));
    }
}
