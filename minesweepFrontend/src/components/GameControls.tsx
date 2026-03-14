import React, { useEffect, useState } from 'react';
import type { Difficulty, GameRule } from '../types';

interface GameControlsProps {
  difficulty: Difficulty;
  gameRule: GameRule;
  minesRemaining: number;
  gameOver: boolean;
  won: boolean;
  started: boolean;
  elapsedSeconds: number;
  aiEnabled: boolean;
  heatmapEnabled: boolean;
  onNewGame: (difficulty: Difficulty) => void;
  onChangeGameRule: (rule: GameRule) => void;
  onToggleAi: (enabled: boolean) => void;
  onToggleHeatmap: (enabled: boolean) => void;
  onShowStats: () => void;
  onShowAiTest: () => void;
  onShowExternalAssist: () => void;
  onShowHelp: () => void;
}

const DIFF_LABELS: Record<Difficulty, string> = {
  EASY:   '初级 (9×9)',
  MEDIUM: '中级 (16×16)',
  HARD:   '高级 (16×30)',
};

const RULE_LABELS: Record<GameRule, string> = {
  SAFE_ZONE:        '安全区域模式',
  SINGLE_CELL_SAFE: '单格安全模式',
};

const GameControls: React.FC<GameControlsProps> = ({
  difficulty, gameRule, minesRemaining, gameOver, won, started,
  elapsedSeconds, aiEnabled, heatmapEnabled,
  onNewGame, onChangeGameRule, onToggleAi, onToggleHeatmap, onShowStats, onShowAiTest, onShowExternalAssist, onShowHelp,
}) => {
  const [timer, setTimer] = useState(0);
  const [startTs, setStartTs] = useState<number | null>(null);

  useEffect(() => {
    if (started && !gameOver && startTs === null) {
      setStartTs(Date.now() - elapsedSeconds * 1000);
    }
    if (!started) {
      setStartTs(null);
      setTimer(0);
    }
  }, [started, gameOver, elapsedSeconds, startTs]);

  useEffect(() => {
    if (gameOver) {
      setTimer(elapsedSeconds);
      return;
    }
    if (!started || startTs === null) return;
    const tick = () => setTimer(Math.floor((Date.now() - startTs) / 1000));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [started, startTs, gameOver, elapsedSeconds]);

  const face = gameOver ? (won ? '😎' : '😵') : '🙂';

  return (
    <div className="game-controls">
      {/* Difficulty selector */}
      <div className="controls-row">
        {(['EASY', 'MEDIUM', 'HARD'] as Difficulty[]).map(d => (
          <button
            key={d}
            className={`diff-btn ${d === difficulty ? 'active' : ''}`}
            onClick={() => onNewGame(d)}
          >
            {DIFF_LABELS[d]}
          </button>
        ))}
      </div>

      {/* Game rule selector */}
      <div className="controls-row">
        {(['SAFE_ZONE', 'SINGLE_CELL_SAFE'] as GameRule[]).map(r => (
          <button
            key={r}
            className={`diff-btn rule-btn ${r === gameRule ? 'active' : ''}`}
            onClick={() => onChangeGameRule(r)}
          >
            {RULE_LABELS[r]}
          </button>
        ))}
      </div>

      {/* Info bar */}
      <div className="controls-row info-row">
        <div className="mine-counter">💣 {minesRemaining}</div>
        <button className="face-btn" onClick={() => onNewGame(difficulty)}>{face}</button>
        <div className="timer">⏱ {timer}s</div>
      </div>

      {/* Action buttons */}
      <div className="controls-row">
        <label className="ai-toggle">
          <span className="ai-toggle-label">🧠 智能辅助</span>
          <div className={`toggle-switch ${aiEnabled ? 'on' : ''}`}
               onClick={() => onToggleAi(!aiEnabled)}>
            <div className="toggle-knob" />
          </div>
        </label>
        <label className="ai-toggle">
          <span className="ai-toggle-label">🗺️ 热力图</span>
          <div className={`toggle-switch ${heatmapEnabled ? 'on' : ''}`}
               onClick={() => onToggleHeatmap(!heatmapEnabled)}>
            <div className="toggle-knob" />
          </div>
        </label>
        <button className="stats-btn" onClick={onShowStats}>📊 统计</button>
        <button className="stats-btn" onClick={onShowAiTest}>🧪 辅助强度测试</button>
        <button className="stats-btn" onClick={onShowExternalAssist}>🔍 其它辅助</button>
        <button className="stats-btn" onClick={onShowHelp}>❓ Help</button>
      </div>
    </div>
  );
};

export default React.memo(GameControls);
