package com.magichear.minesweepBackend.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the ExpertSolver evaluation.
 * Bound from the {@code solver} prefix in application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "solver")
public class SolverProperties {

    /** Maximum cells in a single connected component before falling back to heuristic. */
    private int maxEnumCells = 80;

    /** Maximum valid assignments per component enumeration. */
    private int maxValidAssignments = 200_000;

    private Eval eval = new Eval();

    @Data
    public static class Eval {
        /** Number of games per difficulty in evaluation. */
        private int games = 200;

        /** Random seed for reproducible evaluation. */
        private long seed = 20260221;
    }
}
