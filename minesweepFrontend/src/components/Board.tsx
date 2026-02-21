import React from 'react';
import Cell from './Cell';
import type { PredictionResult, Difficulty } from '../types';

interface BoardProps {
  rows: number;
  cols: number;
  playerBoard: number[][];
  flagged: boolean[][];
  gameOver: boolean;
  prediction: PredictionResult | null;
  showHeatmap: boolean;
  showSafest: boolean;
  onCellClick: (row: number, col: number) => void;
  onCellRightClick: (row: number, col: number) => void;
  difficulty: Difficulty;
}

const CELL_SIZES: Record<Difficulty, number> = {
  EASY: 44,
  MEDIUM: 36,
  HARD: 30,
};

const Board: React.FC<BoardProps> = ({
  rows, cols, playerBoard, flagged, gameOver, prediction,
  showHeatmap, showSafest,
  onCellClick, onCellRightClick, difficulty,
}) => {
  const cellSize = CELL_SIZES[difficulty] || 40;
  return (
    <div
      className="board"
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${cols}, ${cellSize}px)`,
        gridTemplateRows: `repeat(${rows}, ${cellSize}px)`,
        gap: '2px',
        background: 'rgba(0,0,0,0.08)',
        borderRadius: '12px',
        padding: '8px',
        boxShadow: '0 4px 20px rgba(0,0,0,0.1)',
      }}
    >
      {Array.from({ length: rows }, (_, r) =>
        Array.from({ length: cols }, (_, c) => (
          <Cell
            key={`${r}-${c}`}
            value={playerBoard[r]?.[c] ?? -2}
            flagged={flagged[r]?.[c] ?? false}
            prediction={showHeatmap ? prediction?.probabilities?.[r]?.[c] : undefined}
            isSafest={
              showSafest && prediction ? r === prediction.safestRow && c === prediction.safestCol : false
            }
            gameOver={gameOver}
            cellSize={cellSize}
            onClick={() => onCellClick(r, c)}
            onRightClick={() => onCellRightClick(r, c)}
          />
        ))
      )}
    </div>
  );
};

export default React.memo(Board);
