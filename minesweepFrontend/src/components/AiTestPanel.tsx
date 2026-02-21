import React, { useState, useEffect, useRef, useMemo } from 'react';
import type { AiTestResult, AiTestStatus, GameRule } from '../types';
import * as api from '../api/gameApi';

interface AiTestPanelProps {
  visible: boolean;
  onClose: () => void;
}

const AiTestPanel: React.FC<AiTestPanelProps> = ({ visible, onClose }) => {
  const [testName, setTestName] = useState('');
  const [testDifficulty, setTestDifficulty] = useState<'EASY' | 'MEDIUM' | 'HARD'>('EASY');
  const [testGameRule, setTestGameRule] = useState<GameRule>('SAFE_ZONE');
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState(0);
  const [total, setTotal] = useState(100);
  const [currentResult, setCurrentResult] = useState<AiTestResult | null>(null);
  const [allTests, setAllTests] = useState<AiTestResult[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [sortField, setSortField] = useState<'winRate' | 'avgDurationMs' | 'minDurationMs' | 'maxDurationMs' | 'username' | 'createdAt'>('winRate');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const toggleSort = (field: 'winRate' | 'avgDurationMs' | 'minDurationMs' | 'maxDurationMs' | 'username' | 'createdAt') => {
    if (sortField === field) {
      setSortOrder(prev => (prev === 'asc' ? 'desc' : 'asc'));
      return;
    }
    setSortField(field);
    setSortOrder('desc');
  };

  const sortedTests = useMemo(() => {
    const list = [...allTests];
    list.sort((a, b) => {
      const av = sortField === 'createdAt'
        ? new Date(a.createdAt).getTime()
        : a[sortField];
      const bv = sortField === 'createdAt'
        ? new Date(b.createdAt).getTime()
        : b[sortField];

      if (typeof av === 'string' && typeof bv === 'string') {
        const result = av.localeCompare(bv, 'zh-CN');
        return sortOrder === 'asc' ? result : -result;
      }

      if (av === bv) return 0;
      const result = av > bv ? 1 : -1;
      return sortOrder === 'asc' ? result : -result;
    });
    return list;
  }, [allTests, sortField, sortOrder]);

  useEffect(() => {
    if (visible) {
      setLoading(true);
      api.getAllAiTests()
        .then(setAllTests)
        .catch(err => console.error('Failed to load AI tests:', err))
        .finally(() => setLoading(false));
    }
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [visible]);

  const handleStart = async () => {
    if (!testName.trim()) {
      setError('请输入测试名称');
      return;
    }
    setRunning(true);
    setError(null);
    setCurrentResult(null);
    setProgress(0);
    try {
      const status = await api.startAiTest(testName.trim(), testDifficulty, testGameRule);
      setTotal(status.total);

      pollingRef.current = setInterval(async () => {
        try {
          const s: AiTestStatus = await api.getAiTestStatus(status.trackingId);
          setProgress(s.progress);
          if (s.status === 'COMPLETED') {
            clearInterval(pollingRef.current!);
            pollingRef.current = null;
            setRunning(false);
            setCurrentResult(s.result ?? null);
            const tests = await api.getAllAiTests();
            setAllTests(tests);
          } else if (s.status === 'FAILED') {
            clearInterval(pollingRef.current!);
            pollingRef.current = null;
            setRunning(false);
            setError(s.errorMessage || '测试失败');
          }
        } catch {
          // ignore polling errors
        }
      }, 1500);
    } catch (e: unknown) {
      setRunning(false);
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  if (!visible) return null;

  return (
    <div className="stats-overlay" onClick={onClose}>
      <div className="ai-test-panel" onClick={e => e.stopPropagation()}>
        <div className="stats-header">
          <h2>� 辅助强度测试</h2>
          <button className="close-btn" onClick={onClose}>✕</button>
        </div>

        {/* Start new test */}
        <div className="ai-test-form">
          <div className="form-group">
            <label>测试名称</label>
            <input
              type="text"
              value={testName}
              onChange={e => setTestName(e.target.value)}
              placeholder="为本次测试命名"
              disabled={running}
            />
          </div>
          <div className="form-group">
            <label>难度</label>
            <select
              value={testDifficulty}
              onChange={e => setTestDifficulty(e.target.value as 'EASY' | 'MEDIUM' | 'HARD')}
              disabled={running}
            >
              <option value="EASY">初级 (9×9)</option>
              <option value="MEDIUM">中级 (16×16)</option>
              <option value="HARD">高级 (16×30)</option>
            </select>
          </div>
          <div className="form-group">
            <label>游戏规则</label>
            <select
              value={testGameRule}
              onChange={e => setTestGameRule(e.target.value as GameRule)}
              disabled={running}
            >
              <option value="SAFE_ZONE">安全区域模式</option>
              <option value="SINGLE_CELL_SAFE">单格安全模式</option>
            </select>
          </div>
          <button
            className="ai-test-start-btn"
            onClick={handleStart}
            disabled={running}
          >
            {running ? '测试进行中...' : '开始测试 (100局)'}
          </button>
        </div>

        {/* Progress */}
        {running && (
          <div className="ai-test-progress">
            <div className="progress-bar-track">
              <div
                className="progress-bar-fill"
                style={{ width: `${(progress / total) * 100}%` }}
              />
            </div>
            <span className="progress-text">{progress} / {total} 局完成</span>
          </div>
        )}

        {error && <div className="login-error" style={{ marginTop: 8 }}>{error}</div>}

        {/* Latest result */}
        {currentResult && (
          <div className="ai-test-latest">
            <h3>最新结果</h3>
            <div className="ai-result-grid">
              <div>难度: <b>{currentResult.difficulty === 'EASY' ? '初级' : currentResult.difficulty === 'MEDIUM' ? '中级' : currentResult.difficulty === 'HARD' ? '高级' : currentResult.difficulty}</b></div>
              <div>胜率: <b>{(currentResult.winRate * 100).toFixed(1)}%</b></div>
              <div>胜场: <b>{currentResult.wins}/{currentResult.totalGames}</b></div>
              <div>平均耗时: <b>{currentResult.avgDurationMs}ms</b></div>
              <div>最短: <b>{currentResult.minDurationMs}ms</b></div>
              <div>最长: <b>{currentResult.maxDurationMs}ms</b></div>
            </div>
          </div>
        )}

        {/* All tests history */}
        <div className="ai-test-history">
          <h3>历史测试记录</h3>
          {loading && <div className="loading">加载中…</div>}
          {!loading && allTests.length === 0 && <p className="empty-text">暂无测试记录</p>}
          {!loading && allTests.length > 0 && (
            <div className="ai-test-list">
              <table>
                <thead>
                  <tr>
                    <th>名称</th>
                    <th>难度</th>
                    <th>
                      胜率
                      <button className={`sort-btn ${sortField === 'winRate' ? 'active' : ''}`} onClick={() => toggleSort('winRate')}>
                        {sortField === 'winRate' ? (sortOrder === 'asc' ? '↑' : '↓') : '↕'}
                      </button>
                    </th>
                    <th>
                      平均
                      <button className={`sort-btn ${sortField === 'avgDurationMs' ? 'active' : ''}`} onClick={() => toggleSort('avgDurationMs')}>
                        {sortField === 'avgDurationMs' ? (sortOrder === 'asc' ? '↑' : '↓') : '↕'}
                      </button>
                    </th>
                    <th>
                      最短
                      <button className={`sort-btn ${sortField === 'minDurationMs' ? 'active' : ''}`} onClick={() => toggleSort('minDurationMs')}>
                        {sortField === 'minDurationMs' ? (sortOrder === 'asc' ? '↑' : '↓') : '↕'}
                      </button>
                    </th>
                    <th>
                      最长
                      <button className={`sort-btn ${sortField === 'maxDurationMs' ? 'active' : ''}`} onClick={() => toggleSort('maxDurationMs')}>
                        {sortField === 'maxDurationMs' ? (sortOrder === 'asc' ? '↑' : '↓') : '↕'}
                      </button>
                    </th>
                    <th>
                      用户
                      <button className={`sort-btn ${sortField === 'username' ? 'active' : ''}`} onClick={() => toggleSort('username')}>
                        {sortField === 'username' ? (sortOrder === 'asc' ? '↑' : '↓') : '↕'}
                      </button>
                    </th>
                    <th>
                      日期
                      <button className={`sort-btn ${sortField === 'createdAt' ? 'active' : ''}`} onClick={() => toggleSort('createdAt')}>
                        {sortField === 'createdAt' ? (sortOrder === 'asc' ? '↑' : '↓') : '↕'}
                      </button>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {sortedTests.map(t => (
                    <tr key={t.id}>
                      <td title={t.testName}>{t.testName}</td>
                      <td>{t.difficulty === 'EASY' ? '初级' : t.difficulty === 'MEDIUM' ? '中级' : t.difficulty === 'HARD' ? '高级' : t.difficulty}</td>
                      <td>{(t.winRate * 100).toFixed(1)}%</td>
                      <td>{t.avgDurationMs}ms</td>
                      <td>{t.minDurationMs}ms</td>
                      <td>{t.maxDurationMs}ms</td>
                      <td>{t.username}</td>
                      <td>{new Date(t.createdAt).toLocaleDateString('zh-CN')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default AiTestPanel;
