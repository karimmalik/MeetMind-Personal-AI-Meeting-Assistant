const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('meetmind', {
  version: '0.5.4',
  getAppVersion: () => ipcRenderer.invoke('rimeet:app-version'),
  getBackendStatus: () => ipcRenderer.invoke('rimeet:backend-status'),
  getDesktopSourceId: () => ipcRenderer.invoke('rimeet:desktop-source-id'),
  startNativeLiveAudio: () => ipcRenderer.invoke('rimeet:start-native-live-audio'),
  takeNativeLiveChunk: () => ipcRenderer.invoke('rimeet:take-native-live-chunk'),
  stopNativeLiveAudio: () => ipcRenderer.invoke('rimeet:stop-native-live-audio'),
  disposeNativeLiveAudio: () => ipcRenderer.invoke('rimeet:dispose-native-live-audio')
});
