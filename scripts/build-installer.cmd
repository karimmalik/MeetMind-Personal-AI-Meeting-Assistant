@echo off
setlocal EnableExtensions

set "ROOT=%~dp0.."
set "JAVA_HOME=C:\Program Files\OpenJDK\jdk-21"
set "PYTHON=%ROOT%\transcription-worker\.venv\Scripts\python.exe"

if not exist "%JAVA_HOME%\bin\jlink.exe" (
  echo Java 21 was not found at "%JAVA_HOME%".
  exit /b 1
)
if not exist "%PYTHON%" (
  echo The transcription worker is not set up. Run scripts\setup-transcription.cmd first.
  exit /b 1
)

pushd "%ROOT%"
call "%PYTHON%" -m pip install -r transcription-worker\requirements.txt pyinstaller
call "%PYTHON%" -m PyInstaller --noconfirm --clean --onedir --name RIMeetWorker --collect-all faster_whisper --collect-all ctranslate2 transcription-worker\main.py
if errorlevel 1 exit /b 1

call mvn -q -f backend\pom.xml -DskipTests package
if errorlevel 1 exit /b 1

set "MODEL_ROOT=%USERPROFILE%\.cache\huggingface\hub\models--Systran--faster-whisper-base\snapshots"
set "MODEL_SNAPSHOT="
for /d %%D in ("%MODEL_ROOT%\*") do set "MODEL_SNAPSHOT=%%~fD"
if not defined MODEL_SNAPSHOT (
  echo Whisper Base model is missing from the local cache. Run one Base transcription first.
  exit /b 1
)
if exist desktop\resources\models\base rmdir /s /q desktop\resources\models\base
mkdir desktop\resources\models\base
xcopy "%MODEL_SNAPSHOT%\*" desktop\resources\models\base\ /E /I /Y >nul

if exist desktop\resources\runtime\java rmdir /s /q desktop\resources\runtime\java
"%JAVA_HOME%\bin\jlink.exe" --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.crypto.ec,jdk.unsupported --strip-debug --no-header-files --no-man-pages --output desktop\resources\runtime\java
if errorlevel 1 exit /b 1

pushd desktop
call npm ci
if errorlevel 1 exit /b 1
call npm run build:installer
popd
popd
