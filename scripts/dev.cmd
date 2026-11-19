@echo off
setlocal EnableExtensions

set "ROOT=%~dp0.."

echo Building RIMeet backend...
pushd "%ROOT%\backend"
call mvn -q -DskipTests package
if errorlevel 1 (
  echo.
  echo Backend build failed.
  pause
  exit /b 1
)

set "BACKEND_JAR=%CD%\target\rimeet-backend.jar"
if not exist "%BACKEND_JAR%" (
  echo Backend JAR was not created.
  pause
  exit /b 1
)

start "RIMeet Backend" cmd /k "java -jar "%BACKEND_JAR%""
popd

echo Starting RIMeet desktop...
pushd "%ROOT%\desktop"
call npm run dev
popd
