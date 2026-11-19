# MeetMind — Personal AI Meeting Assistant

Windows-only, offline-first desktop foundation for processing meeting audio and video.

## Phase 1

- Electron + React + TypeScript + Vite desktop shell
- Spring Boot local backend
- Backend health status in the desktop UI
- PowerShell development scripts

## Prerequisites

- Node.js 20+
- Java 17+
- Maven 3.9+ (or Maven Wrapper when added)

## Run the backend

```powershell
cd backend
mvn spring-boot:run
```

Verify:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
```

## Run the desktop app

```powershell
cd desktop
npm install
npm run dev
```

Or launch both local services together from the project root:

```powershell
.\scripts\dev.ps1
```

If your PowerShell execution policy blocks local scripts, use the Windows launcher instead:

```powershell
.\scripts\dev.cmd
```

The backend listens only on `127.0.0.1:8081`. Keep its PowerShell window open while using the desktop app.

The next phase will add SQLite, media upload, FFmpeg, and meeting history.
