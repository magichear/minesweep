import React from 'react';

const NUMBER_COLORS: Record<number, string> = {
  1: '#1976D2',
  2: '#388E3C',
  3: '#D32F2F',
  4: '#7B1FA2',
  5: '#E64A19',
  6: '#00838F',
  7: '#424242',
  8: '#9E9E9E',
};

interface CellProps {
  value: number;       // -2=unrevealed, -1=mine, 0-8=number
  flagged: boolean;
  prediction?: number; // mine probability 0-1
  isSafest?: boolean;
  gameOver: boolean;
  cellSize: number;
  onClick: () => void;
  onRightClick: () => void;
}

const Cell: React.FC<CellProps> = ({
  value, flagged, prediction, isSafest, gameOver, cellSize,
  onClick, onRightClick,
}) => {
  const handleClick = (e: React.MouseEvent) => {
    e.preventDefault();
    if (!gameOver && value === -2 && !flagged) onClick();
  };

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    if (!gameOver && value === -2) onRightClick();
  };

  const isRevealed = value >= 0;
  const isMine = value === -1;

  let cls = 'cell';
  if (isRevealed) cls += ' revealed';
  if (isMine) cls += ' mine';
  if (flagged && !isRevealed && !isMine) cls += ' flagged';
  if (isSafest && !isRevealed && !gameOver) cls += ' safest';

  let content: React.ReactNode = '';
  if (flagged && !isRevealed && !isMine) {
    content = '🚩';
  } else if (isMine) {
    content = '💣';
  } else if (isRevealed && value > 0) {
    content = <span style={{ color: NUMBER_COLORS[value], fontWeight: 700 }}>{value}</span>;
  }

  const style: React.CSSProperties = {
    width: cellSize,
    height: cellSize,
    fontSize: cellSize > 36 ? '1rem' : '0.8rem',
  };

  // Heat-map overlay for AI prediction
  if (prediction !== undefined && prediction < 1.0 && !isRevealed && !flagged && !gameOver) {
    const r = Math.round(255 * Math.min(prediction * 3, 1));
    const g = Math.round(255 * Math.min((1 - prediction) * 1.5, 1));
    style.background = `rgba(${r}, ${g}, 0, 0.65)`;
    style.boxShadow = 'inset 0 0 0 1px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.18)';
  }
  if (isSafest && !isRevealed && !gameOver) {
    style.boxShadow = '0 0 10px 3px rgba(0,220,0,0.8)';
    style.zIndex = 1;
    style.border = '2px solid #00cc00';
  }

  return (
    <div
      className={cls}
      style={style}
      onClick={handleClick}
      onContextMenu={handleContextMenu}
    >
      {content}
    </div>
  );
};

export default React.memo(Cell);
