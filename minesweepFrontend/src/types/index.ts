export interface GameState {
  gameId: string;
  rows: number;
  cols: number;
  mines: number;
  difficulty: string;
  gameRule: string;
  playerBoard: number[][];
  flagged: boolean[][];
  gameOver: boolean;
  won: boolean;
  elapsedSeconds: number;
  minesRemaining: number;
}

export interface PredictionResult {
  probabilities: number[][];
  safestRow: number;
  safestCol: number;
}

export interface DifficultyStats {
  totalGames: number;
  totalWins: number;
  winRate: number;
  maxConsecutiveWins: number;
  minDurationSeconds: number | null;
  maxDurationSeconds: number | null;
  topRecords: TopRecord[];
}

export interface TopRecord {
  durationSeconds: number;
  playedAt: string;
}

export interface Stats {
  easy: DifficultyStats;
  medium: DifficultyStats;
  hard: DifficultyStats;
  globalWinRate: number;
  globalTotalGames: number;
}

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type GameRule = 'SAFE_ZONE' | 'SINGLE_CELL_SAFE';

// Auth
export interface AuthInfo {
  token: string;
  username: string;
}

export interface AuthResponse {
  token: string;
  username: string;
}

// AI Test
export interface AiTestStatus {
  trackingId: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  progress: number;
  total: number;
  result?: AiTestResult;
  errorMessage?: string;
}

export interface AiTestResult {
  id: number;
  testName: string;
  modelName: string;
  difficulty: string;
  totalGames: number;
  wins: number;
  winRate: number;
  avgDurationMs: number;
  maxDurationMs: number;
  minDurationMs: number;
  username: string;
  createdAt: string;
}

// External Assist
export interface AnalyzeResponse {
  rows: number;
  cols: number;
  board: number[][];
  probabilities: number[][];
  safestRow: number;
  safestCol: number;
  action: string;
  warnings: string[];
  calibrationOk: boolean;
}
