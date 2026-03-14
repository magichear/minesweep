const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  /** Show the overlay recognition frame on screen. */
  showOverlay: (bounds) => ipcRenderer.invoke('show-overlay', bounds),
  /** Hide the overlay (keep instance). */
  hideOverlay: () => ipcRenderer.invoke('hide-overlay'),
  /** Close and destroy the overlay. */
  closeOverlay: () => ipcRenderer.invoke('close-overlay'),
  /** Get the overlay's inner bounds (excluding border) in screen coords. */
  getOverlayBounds: () => ipcRenderer.invoke('get-overlay-bounds'),
  /** Resize the overlay window. */
  setOverlaySize: (w, h) => ipcRenderer.invoke('set-overlay-size', w, h),
  /** Check if the overlay is currently visible. */
  isOverlayVisible: () => ipcRenderer.invoke('is-overlay-visible'),
  /** Get backend base URL (used to build API requests in Electron mode). */
  getBackendUrl: () => ipcRenderer.invoke('get-backend-url'),
  /** Listen for overlay-closed event. Returns a cleanup function. */
  onOverlayClosed: (callback) => {
    const handler = () => callback();
    ipcRenderer.on('overlay-closed', handler);
    return () => ipcRenderer.removeListener('overlay-closed', handler);
  },
});
