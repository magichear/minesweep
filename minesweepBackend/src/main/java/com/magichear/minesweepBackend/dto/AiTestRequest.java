package com.magichear.minesweepBackend.dto;

import lombok.Data;

@Data
public class AiTestRequest {
    private String testName;
    private String difficulty;
    private String gameRule;
}
