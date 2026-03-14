import { useState, useCallback, useEffect } from 'react';
import Board from './components/Board';
import GameControls from './components/GameControls';
import StatsPanel from './components/StatsPanel';
import LoginPanel from './components/LoginPanel';
import AiTestPanel from './components/AiTestPanel';
import ExternalAssistPanel from './components/ExternalAssistPanel';
import HelpPanel from './components/HelpPanel';
import type { GameState, PredictionResult, Difficulty, GameRule } from './types';
import * as api from './api/gameApi';
import './App.css';

function App() {
  // Auth
  const [auth, setAuth] = useState<{ token: string; username: string } | null>(() => {
    const token = localStorage.getItem('token');
    const username = localStorage.getItem('username');
    return token && username ? { token, username } : null;
  });

  // Game
  const [game, setGame] = useState<GameState | null>(null);
  const [difficulty, setDifficulty] = useState<Difficulty>('EASY');
  const [gameRule, setGameRule] = useState<GameRule>('SAFE_ZONE');
  const [prediction, setPrediction] = useState<PredictionResult | null>(null);
  const [aiEnabled, setAiEnabled] = useState(false);
  const [heatmapEnabled, setHeatmapEnabled] = useState(false);
  const [showStats, setShowStats] = useState(false);
  const [showAiTest, setShowAiTest] = useState(false);
  const [showExternalAssist, setShowExternalAssist] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Set token on mount / auth change
  useEffect(() => {
    if (auth?.token) api.setToken(auth.token);
  }, [auth]);

  /* ---------- Auth ---------- */
  const handleLogin = useCallback((token: string, username: string) => {
    localStorage.setItem('token', token);
    localStorage.setItem('username', username);
    api.setToken(token);
    setAuth({ token, username });
  }, []);

  const handleLogout = useCallback(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    api.setToken(null);
    setAuth(null);
    setGame(null);
    setPrediction(null);
    setAiEnabled(false);
    setHeatmapEnabled(false);
  }, []);

  /* ---------- Game ---------- */
  const handleNewGame = useCallback(async (diff: Difficulty, rule?: GameRule) => {
    try {
      setError(null);
      setPrediction(null);
      setDifficulty(diff);
      if (rule) setGameRule(rule);
      const effectiveRule = rule ?? gameRule;
      const state = await api.createGame(diff, effectiveRule);
      setGame(state);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [gameRule]);

  const handleCellClick = useCallback(async (row: number, col: number) => {
    if (!game || game.gameOver) return;
    try {
      setError(null);
      const state = await api.revealCell(game.gameId, row, col);
      setGame(state);
      // Auto-predict if AI assist or heatmap is ON
      if ((aiEnabled || heatmapEnabled) && !state.gameOver) {
        try {
          const started = state.playerBoard?.some(r => r.some(v => v >= 0));
          if (started) {
            const pred = await api.getPrediction(state.gameId);
            setPrediction(pred);
          }
        } catch {
          // prediction failed, ignore
        }
      } else {
        setPrediction(null);
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [game, aiEnabled, heatmapEnabled]);

  const handleCellRightClick = useCallback(async (row: number, col: number) => {
    if (!game || game.gameOver) return;
    try {
      setError(null);
      const state = await api.flagCell(game.gameId, row, col);
      setGame(state);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [game]);

  /* ---------- AI / Heatmap Toggle ---------- */
  const handleToggleAi = useCallback(async (enabled: boolean) => {
    setAiEnabled(enabled);
    if (!enabled && !heatmapEnabled) {
      setPrediction(null);
      return;
    }
    // Immediately fetch prediction if game is started
    if (enabled && game && !game.gameOver) {
      const started = game.playerBoard?.some(r => r.some(v => v >= 0));
      if (started && !prediction) {
        try {
          const pred = await api.getPrediction(game.gameId);
          setPrediction(pred);
        } catch {
          // ignore
        }
      }
    }
  }, [game, heatmapEnabled, prediction]);

  const handleToggleHeatmap = useCallback(async (enabled: boolean) => {
    setHeatmapEnabled(enabled);
    if (!enabled && !aiEnabled) {
      setPrediction(null);
      return;
    }
    // Immediately fetch prediction if game is started
    if (enabled && game && !game.gameOver) {
      const started = game.playerBoard?.some(r => r.some(v => v >= 0));
      if (started && !prediction) {
        try {
          const pred = await api.getPrediction(game.gameId);
          setPrediction(pred);
        } catch {
          // ignore
        }
      }
    }
  }, [game, aiEnabled, prediction]);

  /* ---------- Derived state ---------- */
  const started = !!game && game.playerBoard?.some(row => row.some(v => v >= 0));

  /* ---------- Render ---------- */
  if (!auth) {
    return <LoginPanel onLogin={handleLogin} />;
  }

  return (
    <div className="app">
      {/* Header */}
      <header className="app-header">
        <div className="header-left">
          <span className="header-icon">💣</span>
          <h1 className="header-title">扫雷</h1>
        </div>
        <div className="header-right">
          <span className="user-badge">👤 {auth.username}</span>
          <button className="logout-btn" onClick={handleLogout}>登出</button>
        </div>
      </header>

      <main className="app-main">
        <GameControls
          difficulty={difficulty}
          gameRule={gameRule}
          minesRemaining={game?.minesRemaining ?? 0}
          gameOver={game?.gameOver ?? false}
          won={game?.won ?? false}
          started={started}
          elapsedSeconds={game?.elapsedSeconds ?? 0}
          aiEnabled={aiEnabled}
          heatmapEnabled={heatmapEnabled}
          onNewGame={handleNewGame}
          onChangeGameRule={(rule: GameRule) => { setGameRule(rule); handleNewGame(difficulty, rule); }}
          onToggleAi={handleToggleAi}
          onToggleHeatmap={handleToggleHeatmap}
          onShowStats={() => setShowStats(true)}
          onShowAiTest={() => setShowAiTest(true)}
          onShowExternalAssist={() => setShowExternalAssist(true)}
          onShowHelp={() => setShowHelp(true)}
        />

        {error && <div className="error-bar">{error}</div>}

        {game ? (
          <div className="game-area">
            {game.gameOver && (
              <div className={`game-result ${game.won ? 'win' : 'lose'}`}>
                {game.won ? '🎉 恭喜胜利！' : '💥 游戏结束！'}
                {game.won && ` 用时: ${game.elapsedSeconds}秒`}
              </div>
            )}
            <Board
              rows={game.rows}
              cols={game.cols}
              playerBoard={game.playerBoard}
              flagged={game.flagged}
              gameOver={game.gameOver}
              prediction={game.gameOver || (!aiEnabled && !heatmapEnabled) ? null : prediction}
              showHeatmap={heatmapEnabled}
              showSafest={aiEnabled}
              onCellClick={handleCellClick}
              onCellRightClick={handleCellRightClick}
              difficulty={difficulty}
            />
          </div>
        ) : (
          <div className="welcome-card">
            <div className="welcome-emoji">💣</div>
            <h2>选择难度开始游戏</h2>
            <div className="rules">
              <h3>游戏规则</h3>
              <ul>
                <li>左键点击翻开格子</li>
                <li>右键点击放置/取消旗帜标记</li>
                <li>数字表示周围 8 格中的地雷数量</li>
                <li>翻开所有非地雷格子即可获胜</li>
                <li>所有难度均可开启智能辅助推荐下一步</li>
                <li>可独立开启热力图查看各格危险概率</li>
              </ul>
            </div>
          </div>
        )}
      </main>

      <StatsPanel visible={showStats} onClose={() => setShowStats(false)} />
      <AiTestPanel visible={showAiTest} onClose={() => setShowAiTest(false)} />
      <ExternalAssistPanel visible={showExternalAssist} onClose={() => setShowExternalAssist(false)} />
      <HelpPanel visible={showHelp} onClose={() => setShowHelp(false)} />
    </div>
  );
}

export default App;
