import type { AnalyzeResponse } from '../types';

const isElectron = typeof window !== 'undefined' && !!(window as Window).electronAPI;
const BASE_URL = isElectron ? 'http://localhost:8080/api/external' : '/api/external';

async function ensureOk(resp: Response): Promise<void> {
  if (resp.ok) return;
  let detail = '';
  try {
    const body = await resp.json();
    detail = body?.warnings?.[0] || body?.error || body?.message || JSON.stringify(body);
  } catch {
    // ignore parse failure
  }
  throw new Error(detail || `HTTP ${resp.status}`);
}

/** Analyze a screen region by capturing from backend. */
export async function analyzeRegion(
  x: number, y: number, width: number, height: number,
  rows: number, cols: number, mines: number, debugMode: boolean
): Promise<AnalyzeResponse> {
  const resp = await fetch(`${BASE_URL}/analyze-region`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ x, y, width, height, rows, cols, mines, debugMode }),
  });
  await ensureOk(resp);
  return resp.json();
}

/** Analyze an uploaded image file. */
export async function analyzeImage(
  file: File, rows: number, cols: number, mines: number, debugMode: boolean
): Promise<AnalyzeResponse> {
  const formData = new FormData();
  formData.append('image', file);
  formData.append('rows', String(rows));
  formData.append('cols', String(cols));
  formData.append('mines', String(mines));
  formData.append('debugMode', String(debugMode));
  const resp = await fetch(`${BASE_URL}/analyze-image`, {
    method: 'POST',
    body: formData,
  });
  await ensureOk(resp);
  return resp.json();
}

/** Click at a screen position (for auto-play). */
export async function autoClick(
  screenX: number, screenY: number, rightClick: boolean
): Promise<void> {
  const resp = await fetch(`${BASE_URL}/click`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ screenX, screenY, rightClick }),
  });
  await ensureOk(resp);
}
