const { ipcRenderer } = require('electron');

/** Border hit area in px — wider than visual border for easy grab. */
const BORDER = 24;
/** Minimum window dimension. */
const MIN_SIZE = 120;

let action = null; // null | 'drag' | 'resize'

/**
 * Determine which zone the cursor is in.
 * Returns 'nw','ne','sw','se' for corners (resize);
 * 'n','s','e','w' for edges (drag);
 * 'center' for click-through area.
 */
function getZone(x, y, w, h) {
  const inLeft = x < BORDER;
  const inRight = x >= w - BORDER;
  const inTop = y < BORDER;
  const inBottom = y >= h - BORDER;

  if (!inLeft && !inRight && !inTop && !inBottom) return 'center';

  // Corners → resize
  if (inLeft && inTop) return 'nw';
  if (inRight && inTop) return 'ne';
  if (inLeft && inBottom) return 'sw';
  if (inRight && inBottom) return 'se';

  // Edges → drag
  if (inTop) return 'n';
  if (inBottom) return 's';
  if (inLeft) return 'w';
  if (inRight) return 'e';
  return 'center';
}

function getCursor(zone) {
  switch (zone) {
    case 'nw': case 'se': return 'nwse-resize';
    case 'ne': case 'sw': return 'nesw-resize';
    case 'n': case 's': return 'ns-resize';
    case 'e': case 'w': return 'ew-resize';
    case 'center': return 'default';
    default: return 'default';
  }
}

function isCorner(zone) {
  return zone === 'nw' || zone === 'ne' || zone === 'sw' || zone === 'se';
}

window.addEventListener('DOMContentLoaded', () => {
  const sizeEl = document.getElementById('size-label');

  function updateSize() {
    if (sizeEl) sizeEl.textContent = `${window.innerWidth} × ${window.innerHeight}`;
  }
  updateSize();
  window.addEventListener('resize', updateSize);

  // ── Mouse move ──
  document.addEventListener('mousemove', (e) => {
    if (action) {
      // During drag/resize, forward cursor position to main process
      ipcRenderer.send('overlay-mouse-move');
      return;
    }
    const zone = getZone(e.clientX, e.clientY, window.innerWidth, window.innerHeight);
    const onBorder = zone !== 'center';
    ipcRenderer.send('overlay-set-ignore-mouse', !onBorder);
    document.body.style.cursor = getCursor(zone);
  });

  // ── Mouse down → start drag or resize ──
  document.addEventListener('mousedown', (e) => {
    if (e.button !== 0) return;
    const zone = getZone(e.clientX, e.clientY, window.innerWidth, window.innerHeight);
    if (zone === 'center') return;

    if (isCorner(zone)) {
      action = 'resize';
      ipcRenderer.send('overlay-start-resize', zone);
    } else {
      action = 'drag';
      ipcRenderer.send('overlay-start-drag');
    }
    e.preventDefault();
  });

  // ── Mouse up → stop ──
  document.addEventListener('mouseup', () => {
    if (action) {
      ipcRenderer.send('overlay-stop-action');
      action = null;
    }
  });

  // ── Mouse leave → resume click-through ──
  document.addEventListener('mouseleave', () => {
    if (!action) {
      ipcRenderer.send('overlay-set-ignore-mouse', true);
    }
  });
});
