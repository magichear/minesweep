package com.magichear.minesweepBackend.entity;

import com.magichear.minesweepBackend.model.Difficulty;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "game_record")
public class GameRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private boolean won;

    private Long durationSeconds;

    @Column(nullable = false)
    private LocalDateTime playedAt;
}
