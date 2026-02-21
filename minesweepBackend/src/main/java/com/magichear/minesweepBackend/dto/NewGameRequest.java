package com.magichear.minesweepBackend.dto;

import lombok.Data;

@Data
public class NewGameRequest {
    private String difficulty;
    private String gameRule;
}
