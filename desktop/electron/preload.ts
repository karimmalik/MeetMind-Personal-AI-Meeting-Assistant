import { contextBridge } from 'electron';

contextBridge.exposeInMainWorld('meetmind', {
  version: '0.1.0'
});
