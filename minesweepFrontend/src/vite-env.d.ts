/// <reference types="vite/client" />

interface ElectronAPI {
  showOverlay: (bounds?: { x?: number; y?: number; width?: number; height?: number }) => Promise<void>;
  hideOverlay: () => Promise<void>;
  closeOverlay: () => Promise<void>;
  getOverlayBounds: () => Promise<{
    x: number; y: number; width: number; height: number;
    rawX: number; rawY: number; rawWidth: number; rawHeight: number;
  } | null>;
  setOverlaySize: (w: number, h: number) => Promise<void>;
  isOverlayVisible: () => Promise<boolean>;
  getBackendUrl: () => Promise<string>;
  onOverlayClosed: (callback: () => void) => () => void;
}

interface Window {
  electronAPI?: ElectronAPI;
}
