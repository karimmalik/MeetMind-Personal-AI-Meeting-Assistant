@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "PYTHON=%ROOT%\transcription-worker\.venv\Scripts\python.exe"

pushd "%ROOT%"
call "%PYTHON%" -m PyInstaller --noconfirm --clean --onedir --name RIMeetWorker --collect-all faster_whisper --collect-all ctranslate2 transcription-worker\main.py > transcription-worker\pyinstaller-task.log 2>&1
popd
