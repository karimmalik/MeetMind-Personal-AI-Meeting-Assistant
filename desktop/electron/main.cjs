const { app, BrowserWindow, desktopCapturer, ipcMain, session } = require('electron');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');

let backendProcess;
let liveCapture;
let backendState = { status: 'starting', message: 'Starting local backend…' };
let backendLogPath;

function setBackendState(status, message) {
  backendState = { status, message, logPath: backendLogPath };
}

function appendBackendLog(message) {
  if (!backendLogPath) return;
  try { fs.appendFileSync(backendLogPath, message); } catch { /* Startup errors still appear in the UI. */ }
}

function backendHealth() {
  return new Promise((resolve) => {
    const request = http.get('http://127.0.0.1:8081/actuator/health', { timeout: 1500 }, (response) => {
      response.resume();
      resolve(response.statusCode === 200);
    });
    request.on('timeout', () => request.destroy());
    request.on('error', () => resolve(false));
  });
}

function codexExecutable() {
  const localAppData = process.env.LOCALAPPDATA;
  const candidate = localAppData && path.join(localAppData, 'Programs', 'OpenAI', 'Codex', 'bin', 'codex.exe');
  return candidate && fs.existsSync(candidate) ? candidate : 'codex';
}

async function getPrimaryScreenSource() {
  const sources = await desktopCapturer.getSources({ types: ['screen'], thumbnailSize: { width: 1, height: 1 } });
  return sources.find((source) => source.id === 'screen:0:0') ?? sources[0] ?? null;
}

function startBackend() {
  if (!app.isPackaged || backendProcess) return;
  const logDirectory = path.join(app.getPath('userData'), 'logs');
  try { fs.mkdirSync(logDirectory, { recursive: true }); } catch (error) {
    setBackendState('failed', `Cannot create the log folder: ${error.message}`);
    return;
  }
  backendLogPath = path.join(logDirectory, 'backend-startup.log');
  appendBackendLog(`\n--- RIMeet ${app.getVersion()} startup ${new Date().toISOString()} ---\n`);
  const java = path.join(process.resourcesPath, 'runtime', 'java', 'bin', 'java.exe');
  const backend = path.join(process.resourcesPath, 'backend', 'rimeet-backend.jar');
  const worker = path.join(process.resourcesPath, 'worker', 'RIMeetWorker.exe');
  const model = path.join(process.resourcesPath, 'models', 'base');
  for (const [label, file] of [['Java runtime', java], ['Backend JAR', backend], ['Transcription worker', worker], ['Whisper model', model]]) {
    if (!fs.existsSync(file)) {
      const message = `${label} is missing from the installation: ${file}. Extract the complete installer again.`;
      appendBackendLog(`${message}\n`);
      setBackendState('failed', message);
      return;
    }
  }
  try {
    backendProcess = spawn(java, ['-jar', backend], {
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
      env: { ...process.env, RIMEET_WORKER_PATH: worker, RIMEET_MODEL_PATH: model, RIMEET_CODEX_PATH: codexExecutable() }
    });
  } catch (error) {
    appendBackendLog(`Could not launch Java: ${error.message}\n`);
    setBackendState('failed', `Could not launch bundled Java: ${error.message}`);
    return;
  }
  backendProcess.stdout.on('data', (chunk) => appendBackendLog(chunk.toString()));
  backendProcess.stderr.on('data', (chunk) => appendBackendLog(chunk.toString()));
  backendProcess.on('error', (error) => {
    appendBackendLog(`Backend process error: ${error.message}\n`);
    setBackendState('failed', `Could not start bundled Java: ${error.message}`);
    backendProcess = undefined;
  });
  backendProcess.on('exit', (code) => {
    appendBackendLog(`Backend exited with code ${code}.\n`);
    if (backendState.status !== 'ready') setBackendState('failed', `Backend exited during startup (code ${code}). Open the startup log for details.`);
    else setBackendState('failed', `Backend stopped unexpectedly (code ${code}). Open the startup log for details.`);
    backendProcess = undefined;
  });
  setBackendState('starting', 'Starting local backend…');
  const startedAt = Date.now();
  const timer = setInterval(async () => {
    if (!backendProcess || backendState.status === 'failed') { clearInterval(timer); return; }
    if (await backendHealth()) { setBackendState('ready', 'Backend online'); clearInterval(timer); return; }
    if (Date.now() - startedAt > 45000) {
      setBackendState('failed', 'Backend did not become ready within 45 seconds. Check the startup log. Port 8081 may already be in use.');
      clearInterval(timer);
    }
  }, 1000);
}

function stopBackend() {
  if (backendProcess && !backendProcess.killed) backendProcess.kill();
}

function nativeAudioHelper() {
  return app.isPackaged
    ? path.join(process.resourcesPath, 'native-audio', 'RIMeetLoopback.exe')
    : path.join(__dirname, '..', 'native-audio', 'bin', 'RIMeetLoopback.exe');
}

function clearLiveCapture() {
  if (!liveCapture) return;
  if (!liveCapture.process.killed) liveCapture.process.kill();
  fs.rmSync(liveCapture.outputDirectory, { recursive: true, force: true });
  liveCapture = undefined;
}

function startNativeLiveCapture() {
  if (liveCapture) throw new Error('A live meeting is already running.');
  const helper = nativeAudioHelper();
  if (!fs.existsSync(helper)) throw new Error('RIMeet audio capture helper is missing. Reinstall this version of RIMeet.');
  const outputDirectory = path.join(app.getPath('temp'), 'RIMeet-live', `${Date.now()}-${Math.random().toString(16).slice(2)}`);
  fs.mkdirSync(outputDirectory, { recursive: true });
  const process = spawn(helper, ['--output-dir', outputDirectory], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  const capture = { process, outputDirectory, chunks: [], finalPath: null, error: null, output: '', finished: null };
  capture.finished = new Promise((resolve) => process.once('close', resolve));
  process.stdout.on('data', (data) => {
    capture.output += data.toString();
    const lines = capture.output.split(/\r?\n/);
    capture.output = lines.pop();
    lines.forEach((line) => {
      const [kind, file] = line.split('\t');
      if (kind === 'CHUNK' && file) capture.chunks.push(file);
      if (kind === 'FINAL' && file) capture.finalPath = file;
      if (kind === 'ERROR') capture.error = file || 'Native audio capture failed.';
    });
  });
  process.stderr.on('data', (data) => { capture.error = data.toString().trim() || capture.error; });
  process.on('error', (error) => { capture.error = error.message; });
  liveCapture = capture;
}

function takeNativeLiveChunk() {
  if (!liveCapture) return null;
  if (liveCapture.error) throw new Error(liveCapture.error);
  const file = liveCapture.chunks.shift();
  if (!file || !fs.existsSync(file)) return null;
  const data = fs.readFileSync(file).toString('base64');
  fs.rmSync(file, { force: true });
  return data;
}

async function stopNativeLiveCapture() {
  if (!liveCapture) throw new Error('No live meeting is running.');
  const capture = liveCapture;
  if (!capture.process.killed && capture.process.exitCode === null) fs.writeFileSync(path.join(capture.outputDirectory, 'stop.signal'), 'stop');
  await capture.finished;
  if (capture.error) throw new Error(capture.error);
  if (!capture.finalPath || !fs.existsSync(capture.finalPath)) throw new Error('RIMeet could not finalize the local audio recording.');
  return fs.readFileSync(capture.finalPath).toString('base64');
}

function createWindow() {
  const window = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 960,
    minHeight: 640,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      preload: path.join(__dirname, 'preload.cjs')
    }
  });

  if (!app.isPackaged) {
    void window.loadURL('http://127.0.0.1:5173');
  } else {
    void window.loadFile(path.join(__dirname, '../dist/index.html'));
  }
}

app.whenReady().then(() => {
  session.defaultSession.setPermissionRequestHandler((_webContents, permission, callback) => {
    callback(permission === 'media' || permission === 'display-capture');
  });
  ipcMain.handle('rimeet:desktop-source-id', async () => {
    const source = await getPrimaryScreenSource();
    if (!source) throw new Error('No display source is available.');
    return source.id;
  });
  ipcMain.handle('rimeet:app-version', () => app.getVersion());
  ipcMain.handle('rimeet:backend-status', () => backendState);
  ipcMain.handle('rimeet:start-native-live-audio', () => startNativeLiveCapture());
  ipcMain.handle('rimeet:take-native-live-chunk', () => takeNativeLiveChunk());
  ipcMain.handle('rimeet:stop-native-live-audio', () => stopNativeLiveCapture());
  ipcMain.handle('rimeet:dispose-native-live-audio', () => clearLiveCapture());
  startBackend();
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('before-quit', () => { clearLiveCapture(); stopBackend(); });
