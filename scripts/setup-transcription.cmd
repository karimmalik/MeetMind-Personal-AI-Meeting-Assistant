@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
pushd "%ROOT%\transcription-worker"
python -m venv .venv
call .venv\Scripts\python -m pip install --upgrade pip
call .venv\Scripts\python -m pip install -r requirements.txt
popd
echo.
echo Local transcription setup is complete.
pause
