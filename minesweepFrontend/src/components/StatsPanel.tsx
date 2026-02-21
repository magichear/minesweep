import React, { useEffect, useMemo, useState } from 'react';
import type { Stats, DifficultyStats, AiTestResult } from '../types';
import * as api from '../api/gameApi';

interface StatsPanelProps {
  visible: boolean;
  onClose: () => void;
}

const StatsPanel: React.FC<StatsPanelProps> = ({ visible, onClose }) => {
  const [stats, setStats] = useState<Stats | null>(null);
  const [aiTests, setAiTests] = useState<AiTestResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [sortField, setSortField] = useState<'winRate' | 'avgDurationMs' | 'minDurationMs' | 'maxDurationMs' | 'createdAt'>('winRate');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');

  const toggleSort = (field: 'winRate' | 'avgDurationMs' | 'minDurationMs' | 'maxDurationMs' | 'createdAt') => {
    if (sortField === field) {
      setSortOrder(prev => (prev === 'asc' ? 'desc' : 'asc'));
      return;
    }
    setSortField(field);
    setSortOrder('desc');
  };

  const sortedAiTests = useMemo(() => {
    const list = [...aiTests];
    list.sort((a, b) => {
      const av = sortField === 'createdAt'
        ? new Date(a.createdAt).getTime()
        : a[sortField];
      const bv = sortField === 'createdAt'
        ? new Date(b.createdAt).getTime()
        : b[sortField];
      if (av === bv) return 0;
      const result = av > bv ? 1 : -1;
      return sortOrder === 'asc' ? result : -result;
    });
    return list;
  }, [aiTests, sortField, sortOrder]);

  useEffect(() => {
    if (visible) {
      setLoading(true);
      Promise.all([
        api.getStats(),
        api.getAllAiTests(),
      ])
        .then(([s, tests]) => { setStats(s); setAiTests(tests); })
        .catch(err => console.error('Failed to load stats:', err))
        .finally(() => setLoading(false));
    }
  }, [visible]);

  if (!visible) return null;

  const renderDifficultyStats = (name: string, ds: DifficultyStats | undefined) => {
    if (!ds) return null;
    return (
      <div className="difficulty-stats" key={name}>
        <h3>{name}</h3>
        <div className="stats-grid">
          <div>总场数: <b>{ds.totalGames}</b></div>
          <div>胜场: <b>{ds.totalWins}</b></div>
          <div>胜率: <b>{(ds.winRate * 100).toFixed(1)}%</b></div>
          <div>最大连胜: <b>{ds.maxConsecutiveWins}</b></div>
          <div>最短耗时: <b>{ds.minDurationSeconds != null ? `${ds.minDurationSeconds}秒` : '-'}</b></div>
          <div>最长耗时: <b>{ds.maxDurationSeconds != null ? `${ds.maxDurationSeconds}秒` : '-'}</b></div>
        </div>
        {ds.topRecords && ds.topRecords.length > 0 && (
          <div className="top-records">
            <h4>🏆 最快胜利</h4>
            <table>
              <thead>
                <tr><th>#</th><th>用时</th><th>日期</th></tr>
              </thead>
              <tbody>
                {ds.topRecords.map((r, i) => (
                  <tr key={i}>
                    <td>{i + 1}</td>
                    <td>{r.durationSeconds}秒</td>
                    <td>{new Date(r.playedAt).toLocaleDateString('zh-CN')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="stats-overlay" onClick={onClose}>
      <div className="stats-panel" onClick={e => e.stopPropagation()}>
        <div className="stats-header">
          <h2>📊 游戏统计</h2>
          <button className="close-btn" onClick={onClose}>✕</button>
        </div>

        {loading && <div className="loading">加载中…</div>}

        {stats && (
          <>
            <div className="global-stats">
              <span>全局总场数: <b>{stats.globalTotalGames}</b></span>
              <span>全局胜率: <b>{(stats.globalWinRate * 100).toFixed(1)}%</b></span>
            </div>
            {renderDifficultyStats('初级 (9×9)', stats.easy)}
            {renderDifficultyStats('中级 (16×16)', stats.medium)}
            {renderDifficultyStats('高级 (16×30)', stats.hard)}
          </>
        )}

        {/* AI Test Records */}
        {aiTests.length > 0 && (
          <div className="ai-test-stats-section">
            <h3>� 辅助强度测试记录</h3>
            <div className="ai-test-list">
              <table>
                <thead>
                  <tr>
                    <th>名称</th>
                    <th>
                      胜率
                      <button className={`sort-btn ${sortField === 'winRate' ? 'active' : ''}`} onClick={() => toggleSort('winRate')}>
                        {sortField === 'winRate' ? (sortOrder === 'asc' ? '↑' : '↓') : '↕'}
                      </button>
                    </th>
                    <th>
                      均时
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
                      日期
                      <button className={`sort-btn ${sortField === 'createdAt' ? 'active' : ''}`} onClick={() => toggleSort('createdAt')}>
                        {sortField === 'createdAt' ? (sortOrder === 'asc' ? '↑' : '↓') : '↕'}
                      </button>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {sortedAiTests.map(t => (
                    <tr key={t.id}>
                      <td title={t.testName}>{t.testName}</td>
                      <td>{(t.winRate * 100).toFixed(1)}%</td>
                      <td>{t.avgDurationMs}ms</td>
                      <td>{t.minDurationMs}ms</td>
                      <td>{t.maxDurationMs}ms</td>
                      <td>{new Date(t.createdAt).toLocaleDateString('zh-CN')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default StatsPanel;
