import type { GameState, PredictionResult, Stats, Difficulty, GameRule, AuthResponse, AiTestStatus, AiTestResult } from '../types';

const isElectron = typeof window !== 'undefined' && !!(window as Window).electronAPI;
const BASE_URL = isElectron ? 'http://localhost:8080/api' : '/api';

let authToken: string | null = null;

export function setToken(token: string | null) {
  authToken = token;
}

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (authToken) {
    headers['Authorization'] = `Bearer ${authToken}`;
  }
  const response = await fetch(url, {
    ...options,
    headers: {
      ...headers,
      ...options?.headers,
    },
  });
  if (response.status === 401) {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    window.location.reload();
    throw new Error('登录已过期，请重新登录');
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: `HTTP ${response.status}` }));
    throw new Error(error.error || `HTTP ${response.status}`);
  }
  return response.json();
}

// Auth
export function login(username: string, password: string): Promise<AuthResponse> {
  return fetchJson(`${BASE_URL}/auth/login`, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function register(username: string, password: string): Promise<AuthResponse> {
  return fetchJson(`${BASE_URL}/auth/register`, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

// Game
export function createGame(difficulty: Difficulty, gameRule?: GameRule): Promise<GameState> {
  return fetchJson(`${BASE_URL}/game/new`, {
    method: 'POST',
    body: JSON.stringify({ difficulty, gameRule }),
  });
}

export function revealCell(gameId: string, row: number, col: number): Promise<GameState> {
  return fetchJson(`${BASE_URL}/game/${gameId}/reveal`, {
    method: 'POST',
    body: JSON.stringify({ row, col }),
  });
}

export function flagCell(gameId: string, row: number, col: number): Promise<GameState> {
  return fetchJson(`${BASE_URL}/game/${gameId}/flag`, {
    method: 'POST',
    body: JSON.stringify({ row, col }),
  });
}

export function getPrediction(gameId: string): Promise<PredictionResult> {
  return fetchJson(`${BASE_URL}/game/${gameId}/predict`, {
    method: 'POST',
  });
}

export function getGameState(gameId: string): Promise<GameState> {
  return fetchJson(`${BASE_URL}/game/${gameId}`);
}

// Stats
export function getStats(): Promise<Stats> {
  return fetchJson(`${BASE_URL}/stats`);
}

// AI Test
export function startAiTest(testName: string, difficulty: string, gameRule?: string): Promise<AiTestStatus> {
  return fetchJson(`${BASE_URL}/ai-test/start`, {
    method: 'POST',
    body: JSON.stringify({ testName, difficulty, gameRule }),
  });
}

export function getAiTestStatus(trackingId: string): Promise<AiTestStatus> {
  return fetchJson(`${BASE_URL}/ai-test/status/${trackingId}`);
}

export function getAllAiTests(): Promise<AiTestResult[]> {
  return fetchJson(`${BASE_URL}/ai-test`);
}
