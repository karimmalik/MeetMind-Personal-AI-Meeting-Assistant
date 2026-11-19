/// <reference types="vite/client" />

interface Window {
  meetmind?: {
    version: string;
    getAppVersion: () => Promise<string>;
    getBackendStatus: () => Promise<{ status: 'starting' | 'ready' | 'failed'; message: string; logPath?: string }>;
    getDesktopSourceId: () => Promise<string>;
    startNativeLiveAudio: () => Promise<void>;
    takeNativeLiveChunk: () => Promise<string | null>;
    stopNativeLiveAudio: () => Promise<string>;
    disposeNativeLiveAudio: () => Promise<void>;
  };
}
