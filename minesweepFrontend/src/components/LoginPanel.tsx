import React, { useState } from 'react';
import * as api from '../api/gameApi';

interface LoginPanelProps {
  onLogin: (token: string, username: string) => void;
}

const LoginPanel: React.FC<LoginPanelProps> = ({ onLogin }) => {
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) {
      setError('请输入用户名和密码');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = isRegister
        ? await api.register(username, password)
        : await api.login(username, password);
      onLogin(res.token, res.username);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-backdrop">
      <div className="login-card">
        <div className="login-header">
          <span className="login-icon">💣</span>
          <h1>扫雷</h1>
          <p className="login-subtitle">Minesweeper with Solver</p>
        </div>

        <div className="login-tabs">
          <button
            className={`login-tab ${!isRegister ? 'active' : ''}`}
            onClick={() => { setIsRegister(false); setError(null); }}
          >
            登录
          </button>
          <button
            className={`login-tab ${isRegister ? 'active' : ''}`}
            onClick={() => { setIsRegister(true); setError(null); }}
          >
            注册
          </button>
        </div>

        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label htmlFor="username">用户名</label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="输入用户名"
              autoComplete="username"
              disabled={loading}
            />
          </div>
          <div className="form-group">
            <label htmlFor="password">密码</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="输入密码"
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              disabled={loading}
            />
          </div>
          {error && <div className="login-error">{error}</div>}
          <button type="submit" className="login-submit" disabled={loading}>
            {loading ? '请稍候...' : (isRegister ? '注册' : '登录')}
          </button>
        </form>

        <p className="login-hint">默认用户: magichear / 111</p>
      </div>
    </div>
  );
};

export default LoginPanel;
