package com.magichear.minesweepBackend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TopRecordVO {
    private long durationSeconds;
    private LocalDateTime playedAt;
}
