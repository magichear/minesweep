package com.magichear.minesweepBackend.dto;

import lombok.Data;

@Data
public class AiTestStatusVO {
    private String trackingId;
    private String status; // RUNNING, COMPLETED, FAILED
    private int progress;
    private int total;
    private AiTestVO result;
    private String errorMessage;
}
