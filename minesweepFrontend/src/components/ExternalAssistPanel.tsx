import React, { useState, useCallback, useRef, useEffect } from 'react';
import type { AnalyzeResponse, Difficulty } from '../types';
import * as externalApi from '../api/externalApi';

interface ExternalAssistPanelProps {
  visible: boolean;
  onClose: () => void;
}

const isElectron = !!(window as Window).electronAPI;
const eAPI = (window as Window).electronAPI;

const DIFFICULTY_CONFIG: Record<Difficulty, { rows: number; cols: number; mines: number; label: string; overlayW: number; overlayH: number }> = {
  EASY:   { rows: 9,  cols: 9,  mines: 10, label: '初级 (9×9, 10雷)', overlayW: 420, overlayH: 420 },
  MEDIUM: { rows: 16, cols: 16, mines: 40, label: '中级 (16×16, 40雷)', overlayW: 620, overlayH: 620 },
  HARD:   { rows: 16, cols: 30, mines: 99, label: '高级 (16×30, 99雷)', overlayW: 920, overlayH: 520 },
};

const NUMBER_COLORS: Record<number, string> = {
  1: '#1976D2', 2: '#388E3C', 3: '#D32F2F', 4: '#7B1FA2',
  5: '#E64A19', 6: '#00838F', 7: '#424242', 8: '#9E9E9E',
};

/** Helper: fetch with better error messages (reads response body on failure). */
async function safeFetch(url: string, init: RequestInit): Promise<Response> {
  const resp = await fetch(url, init);
  if (!resp.ok) {
    let detail = '';
    try {
      const body = await resp.json();
      detail = body?.warnings?.[0] || body?.error || body?.message || JSON.stringify(body);
    } catch { /* ignore */ }
    throw new Error(detail || `HTTP ${resp.status}`);
  }
  return resp;
}

const ExternalAssistPanel: React.FC<ExternalAssistPanelProps> = ({ visible, onClose }) => {
  const [difficulty, setDifficulty] = useState<Difficulty>('EASY');
  const [customRows, setCustomRows] = useState(9);
  const [customCols, setCustomCols] = useState(9);
  const [customMines, setCustomMines] = useState(10);
  const [useCustom, setUseCustom] = useState(false);

  // Manual coordinates (browser-only fallback)
  const [regionX, setRegionX] = useState(100);
  const [regionY, setRegionY] = useState(100);
  const [regionW, setRegionW] = useState(600);
  const [regionH, setRegionH] = useState(600);

  const [debugMode, setDebugMode] = useState(false);
  const [mode, setMode] = useState<'heatmap' | 'autoplay'>('heatmap');
  const [autoPlaying, setAutoPlaying] = useState(false);
  const [autoInterval, setAutoInterval] = useState(1500);
  const [stopOnGuess, setStopOnGuess] = useState(false);

  const [result, setResult] = useState<AnalyzeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [imageFile, setImageFile] = useState<File | null>(null);

  // Electron overlay state
  const [overlayVisible, setOverlayVisible] = useState(false);
  const [overlayBoundsText, setOverlayBoundsText] = useState('');
  const [backendUrl, setBackendUrl] = useState('');
  const [viewport, setViewport] = useState({
    w: typeof window !== 'undefined' ? window.innerWidth : 1280,
    h: typeof window !== 'undefined' ? window.innerHeight : 800,
  });

  const autoTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const autoDeadlineRef = useRef<number | null>(null);
  const clickRepeatRef = useRef<{ x: number; y: number; rightClick: boolean; repeats: number } | null>(null);

  // Resolve backend URL in Electron mode
  useEffect(() => {
    if (isElectron && eAPI) {
      eAPI.getBackendUrl().then(setBackendUrl);
    }
  }, []);

  // Show overlay when panel opens in Electron mode
  useEffect(() => {
    if (!isElectron || !eAPI) return;
    if (visible) {
      const cfg = DIFFICULTY_CONFIG[difficulty];
      eAPI.showOverlay({ width: cfg.overlayW, height: cfg.overlayH });
      setOverlayVisible(true);
      const interval = setInterval(async () => {
        const b = await eAPI.getOverlayBounds();
        if (b) setOverlayBoundsText(`(${b.x}, ${b.y}) ${b.width}×${b.height}`);
      }, 500);
      return () => clearInterval(interval);
    } else {
      eAPI.hideOverlay();
      setOverlayVisible(false);
    }
  }, [visible, difficulty]);

  // Listen for overlay closed externally
  useEffect(() => {
    if (!isElectron || !eAPI) return;
    const cleanup = eAPI.onOverlayClosed(() => setOverlayVisible(false));
    return cleanup;
  }, []);

  // Clean up on unmount
  useEffect(() => {
    return () => {
      if (autoTimerRef.current) clearInterval(autoTimerRef.current);
      autoDeadlineRef.current = null;
      if (isElectron && eAPI) eAPI.hideOverlay();
    };
  }, []);

  useEffect(() => {
    const onResize = () => {
      setViewport({ w: window.innerWidth, h: window.innerHeight });
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const getConfig = useCallback(() => {
    if (useCustom) return { rows: customRows, cols: customCols, mines: customMines };
    return DIFFICULTY_CONFIG[difficulty];
  }, [difficulty, customRows, customCols, customMines, useCustom]);

  /** Build the full API URL. */
  const apiUrl = useCallback((path: string) => {
    if (isElectron && backendUrl) return `${backendUrl}${path}`;
    return path;
  }, [backendUrl]);

  /** Core: overlay bounds → hide → capture → show → return result. */
  const analyzeWithOverlay = useCallback(async (cfg: { rows: number; cols: number; mines: number }) => {
    if (!eAPI) throw new Error('Not in Electron mode');
    const bounds = await eAPI.getOverlayBounds();
    if (!bounds) throw new Error('识别框未打开');

    await eAPI.hideOverlay();
    await new Promise(r => setTimeout(r, 150));

    try {
      const resp = await safeFetch(apiUrl('/api/external/analyze-region'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          x: bounds.x, y: bounds.y, width: bounds.width, height: bounds.height,
          rows: cfg.rows, cols: cfg.cols, mines: cfg.mines, debugMode,
        }),
      });
      return (await resp.json()) as AnalyzeResponse;
    } finally {
      await eAPI.showOverlay();
    }
  }, [apiUrl, debugMode]);

  /** Analyze from uploaded image. */
  const handleAnalyzeImage = useCallback(async () => {
    if (!imageFile) return;
    setLoading(true);
    setError(null);
    try {
      const cfg = getConfig();
      const resp = await externalApi.analyzeImage(imageFile, cfg.rows, cfg.cols, cfg.mines, debugMode);
      setResult(resp);
      if (resp.warnings.length > 0) setError(resp.warnings.join('\n'));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [getConfig, debugMode, imageFile]);

  /** Analyze from overlay or manual region (for heatmap refresh). */
  const handleRefreshHeatmap = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const cfg = getConfig();
      let resp: AnalyzeResponse;
      if (isElectron && eAPI) {
        resp = await analyzeWithOverlay(cfg);
      } else {
        resp = await externalApi.analyzeRegion(
          regionX, regionY, regionW, regionH,
          cfg.rows, cfg.cols, cfg.mines, debugMode
        );
      }
      setResult(resp);
      if (resp.warnings.length > 0) setError(resp.warnings.join('\n'));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [getConfig, regionX, regionY, regionW, regionH, debugMode, analyzeWithOverlay]);

  const getAutoLimitMs = useCallback(() => {
    const cfg = getConfig();
    const safeCells = Math.max(0, cfg.rows * cfg.cols - cfg.mines);
    return Math.max(10_000, safeCells * autoInterval + 10_000);
  }, [getConfig, autoInterval]);

  // Auto-trigger analysis when switching mode.
  const handleModeChange = useCallback((newMode: 'heatmap' | 'autoplay') => {
    setMode(newMode);
    setAutoPlaying(false);
    autoDeadlineRef.current = null;
    clickRepeatRef.current = null;

    if (newMode === 'autoplay' && !imageFile) {
      // Auto-trigger capture analysis so autoplay view can immediately see heatmap.
      setTimeout(() => {
        handleRefreshHeatmap();
      }, 200);
    }
  }, [imageFile, handleRefreshHeatmap]);

  // ── Auto-play ──

  const doAutoStep = useCallback(async () => {
    try {
      if (autoDeadlineRef.current !== null && Date.now() >= autoDeadlineRef.current) {
        setError('已达到自动游玩时限，已自动停止。可再次点击“开始自动游玩”继续。');
        setAutoPlaying(false);
        return;
      }

      const cfg = getConfig();
      let resp: AnalyzeResponse;

      if (isElectron && eAPI) {
        resp = await analyzeWithOverlay(cfg);
      } else {
        resp = await externalApi.analyzeRegion(
          regionX, regionY, regionW, regionH,
          cfg.rows, cfg.cols, cfg.mines, debugMode
        );
      }
      setResult(resp);
      if (resp.warnings.length > 0) setError(resp.warnings.join('\n'));

      if (stopOnGuess && isNoExpandGuess(resp.board, resp.action, resp.safestRow, resp.safestCol)) {
        setError('当前局面无法由已揭开信息继续扩展，自动游玩已停止，请手动决策。');
        setAutoPlaying(false);
        return;
      }

      if (resp.safestRow >= 0 && resp.safestCol >= 0 && resp.action !== 'error') {
        let baseX: number, baseY: number, captureW: number, captureH: number;
        if (isElectron && eAPI) {
          const bounds = await eAPI.getOverlayBounds();
          if (!bounds) { setAutoPlaying(false); return; }
          baseX = bounds.x; baseY = bounds.y;
          captureW = bounds.width; captureH = bounds.height;
        } else {
          baseX = regionX; baseY = regionY;
          captureW = regionW; captureH = regionH;
        }
        const cellW = captureW / resp.cols;
        const cellH = captureH / resp.rows;
        const clickX = Math.round(baseX + resp.safestCol * cellW + cellW / 2);
        const clickY = Math.round(baseY + resp.safestRow * cellH + cellH / 2);
        const rightClick = resp.action === 'mine';

        if (clickRepeatRef.current
          && clickRepeatRef.current.x === clickX
          && clickRepeatRef.current.y === clickY
          && clickRepeatRef.current.rightClick === rightClick
        ) {
          clickRepeatRef.current.repeats += 1;
        } else {
          clickRepeatRef.current = { x: clickX, y: clickY, rightClick, repeats: 1 };
        }

        if ((clickRepeatRef.current?.repeats ?? 0) >= 6) {
          setError('检测到重复点击同一位置，自动游玩已停止，请检查当前识别区域或手动接管。');
          setAutoPlaying(false);
          return;
        }

        if (isElectron && eAPI) {
          await eAPI.hideOverlay();
          await new Promise(r => setTimeout(r, 80));
        }

        if (isElectron && backendUrl) {
          await safeFetch(`${backendUrl}/api/external/click`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ screenX: clickX, screenY: clickY, rightClick }),
          });
        } else {
          await externalApi.autoClick(clickX, clickY, rightClick);
        }

        if (isElectron && eAPI) {
          await new Promise(r => setTimeout(r, 80));
          await eAPI.showOverlay();
        }
      } else {
        setAutoPlaying(false);
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
      setAutoPlaying(false);
    }
  }, [getConfig, regionX, regionY, regionW, regionH, debugMode, analyzeWithOverlay, backendUrl, stopOnGuess]);

  useEffect(() => {
    if (autoPlaying && mode === 'autoplay') {
      autoTimerRef.current = setInterval(doAutoStep, autoInterval);
      return () => { if (autoTimerRef.current) clearInterval(autoTimerRef.current); };
    }
    if (autoTimerRef.current) {
      clearInterval(autoTimerRef.current);
      autoTimerRef.current = null;
    }
  }, [autoPlaying, mode, doAutoStep, autoInterval]);

  const handleStartAutoPlay = useCallback(() => {
    autoDeadlineRef.current = Date.now() + getAutoLimitMs();
    clickRepeatRef.current = null;
    setAutoPlaying(true);
    doAutoStep();
  }, [doAutoStep, getAutoLimitMs]);

  const handleStopAutoPlay = useCallback(() => {
    autoDeadlineRef.current = null;
    setAutoPlaying(false);
  }, []);

  const toggleOverlay = useCallback(async () => {
    if (!eAPI) return;
    if (overlayVisible) {
      await eAPI.hideOverlay();
      setOverlayVisible(false);
    } else {
      const cfg = DIFFICULTY_CONFIG[difficulty];
      await eAPI.showOverlay({ width: cfg.overlayW, height: cfg.overlayH });
      setOverlayVisible(true);
    }
  }, [overlayVisible, difficulty]);

  const handleDifficultyChange = useCallback((d: Difficulty) => {
    setDifficulty(d);
    setUseCustom(false);
    if (isElectron && eAPI && overlayVisible) {
      const cfg = DIFFICULTY_CONFIG[d];
      eAPI.setOverlaySize(cfg.overlayW, cfg.overlayH);
    }
  }, [overlayVisible]);

  const handleClose = useCallback(() => {
    autoDeadlineRef.current = null;
    setAutoPlaying(false);
    if (isElectron && eAPI) eAPI.hideOverlay();
    setOverlayVisible(false);
    onClose();
  }, [onClose]);

  if (!visible) return null;

  const boardCellSize = result
    ? getCellSize(result.rows, result.cols, viewport.w, viewport.h)
    : 26;

  return (
    <div className="stats-overlay" onClick={handleClose}>
      <div className="external-panel" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="stats-header">
          <h2>🔍 外部扫雷辅助</h2>
          <button className="close-btn" onClick={handleClose}>×</button>
        </div>

        {/* Difficulty selector */}
        <div className="ext-section">
          <h3>棋盘配置</h3>
          <div className="ext-row">
            {(['EASY', 'MEDIUM', 'HARD'] as Difficulty[]).map(d => (
              <button
                key={d}
                className={`diff-btn ${!useCustom && d === difficulty ? 'active' : ''}`}
                onClick={() => handleDifficultyChange(d)}
              >
                {DIFFICULTY_CONFIG[d].label}
              </button>
            ))}
            <button
              className={`diff-btn ${useCustom ? 'active' : ''}`}
              onClick={() => setUseCustom(true)}
            >
              自定义
            </button>
          </div>
          {useCustom && (
            <div className="ext-row" style={{ gap: 8 }}>
              <label>行<input type="number" min={1} max={50} value={customRows}
                onChange={e => setCustomRows(Number(e.target.value))} style={{ width: 50 }} /></label>
              <label>列<input type="number" min={1} max={50} value={customCols}
                onChange={e => setCustomCols(Number(e.target.value))} style={{ width: 50 }} /></label>
              <label>雷<input type="number" min={1} max={500} value={customMines}
                onChange={e => setCustomMines(Number(e.target.value))} style={{ width: 50 }} /></label>
            </div>
          )}
        </div>

        {/* Capture region / Overlay control */}
        <div className="ext-section">
          <h3>识别区域</h3>
          {isElectron ? (
            <>
              <div className="ext-row" style={{ gap: 10 }}>
                <button className={`diff-btn ${overlayVisible ? 'active' : ''}`} onClick={toggleOverlay}>
                  {overlayVisible ? '📐 隐藏识别框' : '📐 显示识别框'}
                </button>
                {overlayBoundsText && (
                  <span style={{ fontSize: '0.82rem', color: '#666' }}>
                    {overlayBoundsText}
                  </span>
                )}
              </div>
              <div style={{ fontSize: '0.8rem', color: '#999', marginTop: 6 }}>
                拖动边框移动位置，拖动四角缩放大小
              </div>
            </>
          ) : (
            <div className="ext-row" style={{ gap: 8 }}>
              <label>X<input type="number" value={regionX} onChange={e => setRegionX(Number(e.target.value))} style={{ width: 70 }} /></label>
              <label>Y<input type="number" value={regionY} onChange={e => setRegionY(Number(e.target.value))} style={{ width: 70 }} /></label>
              <label>宽<input type="number" value={regionW} onChange={e => setRegionW(Number(e.target.value))} style={{ width: 70 }} /></label>
              <label>高<input type="number" value={regionH} onChange={e => setRegionH(Number(e.target.value))} style={{ width: 70 }} /></label>
            </div>
          )}
          {/* Upload image + analyze button */}
          <div className="ext-row" style={{ marginTop: 8, alignItems: 'center' }}>
            <label style={{ fontSize: '0.85rem', color: '#666' }}>
              或上传截图：
              <input type="file" accept="image/*" onChange={e => setImageFile(e.target.files?.[0] ?? null)} style={{ marginLeft: 8 }} />
            </label>
            {imageFile && (
              <>
                <button className="diff-btn" onClick={() => setImageFile(null)} style={{ padding: '4px 10px' }}>
                  清除
                </button>
                <button className="ai-test-start-btn" onClick={handleAnalyzeImage} disabled={loading}
                  style={{ padding: '4px 14px', fontSize: '0.85rem' }}>
                  {loading ? '识别中...' : '📸 截图分析'}
                </button>
              </>
            )}
          </div>
        </div>

        {/* Mode selector */}
        <div className="ext-section">
          <h3>辅助模式</h3>
          <div className="ext-row">
            <button className={`diff-btn ${mode === 'heatmap' ? 'active' : ''}`}
              onClick={() => handleModeChange('heatmap')}>
              🗺️ 热力图
            </button>
            <button className={`diff-btn ${mode === 'autoplay' ? 'active' : ''}`}
              onClick={() => handleModeChange('autoplay')}>
              🤖 自动游玩
            </button>
          </div>
        </div>

        {/* Controls */}
        <div className="ext-section">
          <div className="ext-row">
            <label className="ai-toggle">
              <span className="ai-toggle-label">🐛 调试模式</span>
              <div className={`toggle-switch ${debugMode ? 'on' : ''}`}
                onClick={() => setDebugMode(!debugMode)}>
                <div className="toggle-knob" />
              </div>
            </label>
            {mode === 'autoplay' && (
              <label style={{ fontSize: '0.85rem', color: '#555' }}>
                间隔(ms)
                <input type="number" min={200} max={10000} value={autoInterval}
                  onChange={e => setAutoInterval(Number(e.target.value))}
                  style={{ width: 70, marginLeft: 4 }} />
              </label>
            )}
            {mode === 'autoplay' && (
              <label className="ai-toggle" style={{ marginLeft: 6 }}>
                <span className="ai-toggle-label">猜测停手</span>
                <div className={`toggle-switch ${stopOnGuess ? 'on' : ''}`}
                  onClick={() => setStopOnGuess(!stopOnGuess)}>
                  <div className="toggle-knob" />
                </div>
              </label>
            )}
          </div>
          {mode === 'autoplay' && (
            <div style={{ marginTop: 8, fontSize: '0.82rem', color: '#666' }}>
              本轮自动游玩时限：{formatDurationMs(getAutoLimitMs())}
              <span style={{ marginLeft: 12, color: '#B45309' }}>最短间隔建议设置为250ms</span>
            </div>
          )}
          {mode === 'autoplay' && (
            <div style={{ marginTop: 6, fontSize: '0.82rem', color: '#666' }}>
              建议在桌面背景中游玩，否则鼠标可能误触
            </div>
          )}
          <div className="ext-row" style={{ marginTop: 10 }}>
            {mode === 'heatmap' ? (
              <button className="ai-test-start-btn" onClick={handleRefreshHeatmap} disabled={loading}>
                {loading ? '识别中...' : '🔄 刷新热力图'}
              </button>
            ) : (
              <>
                {!autoPlaying ? (
                  <button className="ai-test-start-btn" onClick={handleStartAutoPlay}>▶ 开始自动游玩</button>
                ) : (
                  <button className="ai-test-start-btn" onClick={handleStopAutoPlay}
                    style={{ background: 'linear-gradient(135deg, #ef5b5b, #f77a7a)' }}>
                    ⏹ 停止
                  </button>
                )}
              </>
            )}
          </div>
        </div>

        {/* Error / warnings */}
        {error && <div className="ext-error">{error}</div>}

        {/* Results: Board + Heatmap */}
        {result && result.rows > 0 && (
          <div className="ext-section">
            <h3>
              识别结果 ({result.rows}×{result.cols})
              {result.calibrationOk
                ? <span style={{ color: '#388E3C', marginLeft: 8 }}>✓ 校对通过</span>
                : <span style={{ color: '#D32F2F', marginLeft: 8 }}>✗ 尺寸不匹配</span>
              }
            </h3>
            <div className="ext-board-wrapper">
              <div className="ext-board" style={{
                gridTemplateColumns: `repeat(${result.cols}, ${boardCellSize}px)`,
              }}>
                {result.board.map((row, r) =>
                  row.map((cell, c) => {
                    const prob = result.probabilities[r]?.[c] ?? 0;
                    const isSafest = r === result.safestRow && c === result.safestCol;
                    return (
                      <ExtCell
                        key={`${r}-${c}`}
                        value={cell}
                        prob={prob}
                        isSafest={isSafest}
                        size={boardCellSize}
                      />
                    );
                  })
                )}
              </div>
            </div>
            {result.safestRow >= 0 && (
              <div style={{ marginTop: 8, fontSize: '0.9rem', color: '#555' }}>
                建议操作: <strong>{result.action === 'safe' ? '安全' : result.action === 'mine' ? '标雷' : '猜测'}</strong>
                {' '}→ ({result.safestRow}, {result.safestCol})
                {result.action === 'guess' &&
                  <span style={{ color: '#E64A19' }}> ⚠ 无确定安全格，需要猜测</span>}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

function getCellSize(rows: number, cols: number, viewportWidth: number, viewportHeight: number): number {
  const panelMaxWidth = Math.min(Math.floor(viewportWidth * 0.92), 1200);
  const availableWidth = Math.max(260, panelMaxWidth - 100);
  const availableHeight = Math.max(220, Math.floor(viewportHeight * 0.52));
  const gap = 2;

  const byWidth = Math.floor((availableWidth - (cols - 1) * gap) / cols);
  const byHeight = Math.floor((availableHeight - (rows - 1) * gap) / rows);
  return Math.max(14, Math.min(44, byWidth, byHeight));
}

function formatDurationMs(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}分${seconds.toString().padStart(2, '0')}秒`;
}

function isNoExpandGuess(board: number[][], action: string, row: number, col: number): boolean {
  if (action !== 'guess') return false;
  if (row < 0 || col < 0 || row >= board.length || col >= (board[0]?.length ?? 0)) return false;

  // If guessed cell has no revealed-number neighbors, it is likely a remote blind guess,
  // meaning current revealed information cannot keep expanding the frontier.
  for (let dr = -1; dr <= 1; dr++) {
    for (let dc = -1; dc <= 1; dc++) {
      if (dr === 0 && dc === 0) continue;
      const nr = row + dr;
      const nc = col + dc;
      if (nr < 0 || nr >= board.length || nc < 0 || nc >= board[0].length) continue;
      const v = board[nr][nc];
      if (v >= 0 && v <= 8) return false;
    }
  }
  return true;
}

interface ExtCellProps {
  value: number;
  prob: number;
  isSafest: boolean;
  size: number;
}

const ExtCell: React.FC<ExtCellProps> = ({ value, prob, isSafest, size }) => {
  const isRevealed = value >= 0 && value <= 8;
  const isMine = value === -1;
  const isFlag = value === -3;
  const isUnrevealed = value === 9;

  let bg = '';
  let content: React.ReactNode = '';
  let border = '';

  if (isRevealed) {
    bg = '#f5f5f5';
    if (value > 0) {
      content = <span style={{ color: NUMBER_COLORS[value], fontWeight: 700 }}>{value}</span>;
    }
  } else if (isMine) {
    bg = '#ff6b6b';
    content = '💣';
  } else if (isFlag) {
    bg = 'linear-gradient(145deg, #8BA8CC, #6D90B8)';
    content = '🚩';
  } else if (isUnrevealed) {
    // Heatmap coloring for unrevealed cells
    const r = Math.round(255 * Math.min(prob * 3, 1));
    const g = Math.round(255 * Math.min((1 - prob) * 1.5, 1));
    bg = `rgba(${r}, ${g}, 0, 0.65)`;
  }

  if (isSafest && isUnrevealed) {
    border = '2px solid #00cc00';
  }

  const style: React.CSSProperties = {
    width: size, height: size,
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    fontSize: size > 30 ? '0.8rem' : '0.65rem',
    fontWeight: 700,
    borderRadius: 3,
    background: bg || 'linear-gradient(145deg, #8BA8CC, #6D90B8)',
    border: border || '1px solid rgba(0,0,0,0.1)',
    boxShadow: isSafest && isUnrevealed ? '0 0 8px 3px rgba(0,200,0,0.6)' : undefined,
    position: 'relative',
    cursor: 'default',
    color: isRevealed ? '#333' : '#fff',
  };

  return <div style={style}>{content}</div>;
};

export default ExternalAssistPanel;
