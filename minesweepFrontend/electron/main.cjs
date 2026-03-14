const { app, BrowserWindow, ipcMain, screen } = require('electron');
const path = require('path');
const fs = require('fs');
const net = require('net');
const { spawn } = require('child_process');

/** Visual border width of the overlay frame (px). */
const OVERLAY_BORDER = 10;
const DEV_URL = 'http://localhost:5173';
const BACKEND_URL = 'http://localhost:8080';
const BACKEND_PORT = 8080;

let mainWindow = null;
let overlayWindow = null;
let backendProcess = null;

// Custom drag/resize state
let overlayAction = null; // { type, startCursor, startBounds, edge? }

// ───────── Window creation ─────────

function getBundledJavaExe() {
  if (!app.isPackaged) {
    return process.platform === 'win32' ? 'java.exe' : 'java';
  }
  const javaExeName = process.platform === 'win32' ? 'java.exe' : 'java';
  return path.join(process.resourcesPath, 'runtime', 'bin', javaExeName);
}

function getBundledJarPath() {
  if (!app.isPackaged) {
    return null;
  }
  return path.join(process.resourcesPath, 'backend', 'minesweepBackend.jar');
}

function waitForBackendReady(port, timeoutMs = 20000) {
  const start = Date.now();
  return new Promise((resolve) => {
    const tryConnect = () => {
      const socket = new net.Socket();
      socket.setTimeout(800);
      socket.once('connect', () => {
        socket.destroy();
        resolve(true);
      });
      const onFail = () => {
        socket.destroy();
        if (Date.now() - start >= timeoutMs) {
          resolve(false);
          return;
        }
        setTimeout(tryConnect, 400);
      };
      socket.once('error', onFail);
      socket.once('timeout', onFail);
      socket.connect(port, '127.0.0.1');
    };
    tryConnect();
  });
}

async function startBackendIfNeeded() {
  if (backendProcess) return;

  const javaExe = getBundledJavaExe();
  const jarPath = getBundledJarPath();
  const userDataDir = app.getPath('userData');
  const dataDir = path.join(userDataDir, 'data');
  fs.mkdirSync(dataDir, { recursive: true });

  if (app.isPackaged && !fs.existsSync(javaExe)) {
    throw new Error(`Bundled Java runtime not found: ${javaExe}`);
  }
  if (app.isPackaged && (!jarPath || !fs.existsSync(jarPath))) {
    throw new Error(`Bundled backend jar not found: ${jarPath}`);
  }

  const args = [
    '-jar',
    jarPath || path.join(process.cwd(), '..', 'dist', 'minesweepBackend.jar'),
    `--spring.datasource.url=jdbc:h2:file:${path.join(dataDir, 'minesweep')}`,
    '--spring.main.headless=false',
    `--server.port=${BACKEND_PORT}`,
  ];

  backendProcess = spawn(javaExe, args, {
    windowsHide: true,
    stdio: 'ignore',
  });

  backendProcess.once('exit', () => {
    backendProcess = null;
  });

  await waitForBackendReady(BACKEND_PORT, 25000);
}

function stopBackendIfRunning() {
  if (!backendProcess) return;
  try {
    backendProcess.kill();
  } catch {
    // ignore cleanup failure
  }
  backendProcess = null;
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 560,
    height: 760,
    minWidth: 400,
    minHeight: 500,
    title: '扫雷辅助',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  if (app.isPackaged) {
    mainWindow.loadFile(path.join(__dirname, '..', 'dist', 'index.html'));
  } else {
    // Dev mode: retry loading until Vite dev server is ready
    const tryLoad = () => {
      mainWindow.loadURL(DEV_URL).catch(() => setTimeout(tryLoad, 800));
    };
    tryLoad();
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
    if (overlayWindow) {
      overlayWindow.close();
      overlayWindow = null;
    }
  });
}

function createOverlay(bounds) {
  if (overlayWindow) {
    overlayWindow.show();
    overlayWindow.focus();
    return;
  }

  const x = bounds?.x ?? 200;
  const y = bounds?.y ?? 200;
  const w = bounds?.width ?? 500;
  const h = bounds?.height ?? 500;

  overlayWindow = new BrowserWindow({
    x, y,
    width: w,
    height: h,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    resizable: false, // we handle resize ourselves since native resize is disabled on transparent windows
    skipTaskbar: true,
    hasShadow: false,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    webPreferences: {
      preload: path.join(__dirname, 'overlayPreload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  overlayWindow.loadFile(path.join(__dirname, 'overlay.html'));
  overlayWindow.setIgnoreMouseEvents(true, { forward: true });

  overlayWindow.on('closed', () => {
    overlayWindow = null;
    overlayAction = null;
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('overlay-closed');
    }
  });
}

// ───────── IPC handlers ─────────

ipcMain.handle('show-overlay', (_event, bounds) => {
  createOverlay(bounds);
});

ipcMain.handle('hide-overlay', () => {
  if (overlayWindow && !overlayWindow.isDestroyed()) overlayWindow.hide();
});

ipcMain.handle('close-overlay', () => {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    overlayWindow.close();
    overlayWindow = null;
  }
});

ipcMain.handle('get-overlay-bounds', () => {
  if (!overlayWindow || overlayWindow.isDestroyed()) return null;
  const bounds = overlayWindow.getBounds();
  return {
    x: bounds.x + OVERLAY_BORDER,
    y: bounds.y + OVERLAY_BORDER,
    width: bounds.width - 2 * OVERLAY_BORDER,
    height: bounds.height - 2 * OVERLAY_BORDER,
    rawX: bounds.x,
    rawY: bounds.y,
    rawWidth: bounds.width,
    rawHeight: bounds.height,
  };
});

ipcMain.handle('set-overlay-size', (_event, width, height) => {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    overlayWindow.setSize(Math.round(width), Math.round(height));
  }
});

ipcMain.handle('is-overlay-visible', () => {
  return !!(overlayWindow && !overlayWindow.isDestroyed() && overlayWindow.isVisible());
});

ipcMain.handle('get-backend-url', () => {
  return BACKEND_URL;
});

// Toggle mouse passthrough
ipcMain.on('overlay-set-ignore-mouse', (_event, ignore) => {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    if (ignore) {
      overlayWindow.setIgnoreMouseEvents(true, { forward: true });
    } else {
      overlayWindow.setIgnoreMouseEvents(false);
    }
  }
});

// ───────── Custom drag / resize ─────────

ipcMain.on('overlay-start-drag', () => {
  if (!overlayWindow || overlayWindow.isDestroyed()) return;
  const cursor = screen.getCursorScreenPoint();
  const bounds = overlayWindow.getBounds();
  overlayAction = { type: 'drag', startCursor: cursor, startBounds: bounds };
});

ipcMain.on('overlay-start-resize', (_event, edge) => {
  if (!overlayWindow || overlayWindow.isDestroyed()) return;
  const cursor = screen.getCursorScreenPoint();
  const bounds = overlayWindow.getBounds();
  overlayAction = { type: 'resize', startCursor: cursor, startBounds: { ...bounds }, edge };
});

ipcMain.on('overlay-mouse-move', () => {
  if (!overlayWindow || overlayWindow.isDestroyed() || !overlayAction) return;
  const cursor = screen.getCursorScreenPoint();
  const dx = cursor.x - overlayAction.startCursor.x;
  const dy = cursor.y - overlayAction.startCursor.y;
  const sb = overlayAction.startBounds;

  if (overlayAction.type === 'drag') {
    overlayWindow.setPosition(sb.x + dx, sb.y + dy);
  } else if (overlayAction.type === 'resize') {
    let x = sb.x, y = sb.y, w = sb.width, h = sb.height;
    const edge = overlayAction.edge;
    if (edge.includes('e')) { w = sb.width + dx; }
    if (edge.includes('w')) { x = sb.x + dx; w = sb.width - dx; }
    if (edge.includes('s')) { h = sb.height + dy; }
    if (edge.includes('n')) { y = sb.y + dy; h = sb.height - dy; }
    if (w < 120) { if (edge.includes('w')) x = sb.x + sb.width - 120; w = 120; }
    if (h < 120) { if (edge.includes('n')) y = sb.y + sb.height - 120; h = 120; }
    overlayWindow.setBounds({ x, y, width: w, height: h });
  }
});

ipcMain.on('overlay-stop-action', () => {
  overlayAction = null;
});

// ───────── Lifecycle ─────────

app.whenReady().then(async () => {
  try {
    await startBackendIfNeeded();
  } catch (err) {
    console.error('Failed to start backend:', err);
  }
  createMainWindow();
});

app.on('window-all-closed', () => {
  app.quit();
});

app.on('before-quit', () => {
  stopBackendIfRunning();
});

app.on('activate', () => {
  if (!mainWindow) createMainWindow();
});
