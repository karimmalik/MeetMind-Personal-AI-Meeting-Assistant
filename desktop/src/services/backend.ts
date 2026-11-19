const API_BASE = 'http://127.0.0.1:8081';

export type BackendHealth = 'checking' | 'online' | 'offline';
export type ProcessingMode = 'AUDIO_ONLY' | 'AUDIO_VIDEO';

export type Meeting = {
  id: string;
  title: string;
  originalFilename: string;
  sourceMediaType: 'AUDIO' | 'VIDEO';
  processingMode: ProcessingMode;
  status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  sizeBytes: number;
  createdAt: string;
  errorMessage: string | null;
};
export type Transcript = { language: string; duration: number; segments: { start: number; end: number; text: string }[] };
export type Insight = { status: 'GENERATING' | 'COMPLETED' | 'FAILED'; content: string | null; errorMessage: string | null };
export type MeetingQuestion = { id: string; question: string; scope: 'MEETING' | 'GLOBAL'; status: 'GENERATING' | 'COMPLETED' | 'FAILED'; answer: string | null; errorMessage: string | null; createdAt: string };
export type InsightHistory = { id: number; content: string; createdAt: string };
export type VideoFrame = { timestampMs: number; imagePath: string };

export async function checkBackend(): Promise<BackendHealth> {
  try {
    const response = await fetch(`${API_BASE}/actuator/health`);
    return response.ok ? 'online' : 'offline';
  } catch {
    return 'offline';
  }
}

export async function getMeetings(): Promise<Meeting[]> {
  const response = await fetch(`${API_BASE}/api/v1/meetings`);
  if (!response.ok) throw new Error('Could not load meeting history.');
  return response.json() as Promise<Meeting[]>;
}

export async function createMeeting(file: File, title: string, processingMode: ProcessingMode): Promise<Meeting> {
  const body = new FormData();
  body.append('file', file);
  body.append('title', title);
  body.append('processingMode', processingMode);
  const response = await fetch(`${API_BASE}/api/v1/meetings`, { method: 'POST', body });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'Could not save the meeting.' })) as { message?: string };
    throw new Error(error.message ?? 'Could not save the meeting.');
  }
  return response.json() as Promise<Meeting>;
}

export async function getTranscript(id: string): Promise<Transcript> {
  const response = await fetch(`${API_BASE}/api/v1/meetings/${id}/transcript`);
  if (!response.ok) throw new Error('Transcript is not ready yet.');
  return response.json() as Promise<Transcript>;
}
export async function generateInsights(id: string) { const response = await fetch(`${API_BASE}/api/v1/meetings/${id}/insights`, { method: 'POST' }); if (!response.ok) throw new Error('Could not start Codex analysis.'); }
export async function getInsights(id: string): Promise<Insight | null> { const response = await fetch(`${API_BASE}/api/v1/meetings/${id}/insights`); if (response.status === 404) return null; if (!response.ok) throw new Error('Could not load insights.'); return response.json() as Promise<Insight>; }
export async function askQuestion(id: string, question: string, scope: 'MEETING' | 'GLOBAL') { const response = await fetch(`${API_BASE}/api/v1/meetings/${id}/questions`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ question, scope }) }); if (!response.ok) throw new Error('Could not send question.'); }
export async function getQuestions(id: string): Promise<MeetingQuestion[]> { const response = await fetch(`${API_BASE}/api/v1/meetings/${id}/questions`); if (!response.ok) throw new Error('Could not load questions.'); return response.json() as Promise<MeetingQuestion[]>; }
export async function getInsightHistory(id: string): Promise<InsightHistory[]> { const response = await fetch(`${API_BASE}/api/v1/meetings/${id}/insights/history`); if (!response.ok) throw new Error('Could not load saved insights.'); return response.json() as Promise<InsightHistory[]>; }
export async function retryMeeting(id: string) { const response = await fetch(`${API_BASE}/api/v1/meetings/${id}/retry`, { method: 'POST' }); if (!response.ok) throw new Error('Could not retry this meeting.'); }
export async function deleteMeeting(id: string) { const response = await fetch(`${API_BASE}/api/v1/meetings/${id}`, { method: 'DELETE' }); if (!response.ok) throw new Error('Could not delete this meeting.'); }
export async function getAppSettings(): Promise<Record<string, string>> { const response = await fetch(`${API_BASE}/api/v1/settings`); if (!response.ok) throw new Error('Could not load settings.'); return response.json() as Promise<Record<string, string>>; }
export async function saveAppSettings(settings: Record<string, string>): Promise<Record<string, string>> { const response = await fetch(`${API_BASE}/api/v1/settings`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(settings) }); if (!response.ok) throw new Error('Could not save settings.'); return response.json() as Promise<Record<string, string>>; }
export async function getFrames(id: string): Promise<VideoFrame[]> { const response = await fetch(`${API_BASE}/api/v1/meetings/${id}/frames`); if (!response.ok) throw new Error('Could not load video frames.'); return response.json() as Promise<VideoFrame[]>; }
export const frameImageUrl = (meetingId: string, timestampMs: number) => `${API_BASE}/api/v1/meetings/${meetingId}/frames/${timestampMs}`;
export async function getActionStates(id: string): Promise<Record<number, boolean>> { const response = await fetch(`${API_BASE}/api/v1/meetings/${id}/actions`); if (!response.ok) throw new Error('Could not load action states.'); return response.json() as Promise<Record<number, boolean>>; }
export async function setActionState(id: string, index: number, completed: boolean) { const response = await fetch(`${API_BASE}/api/v1/meetings/${id}/actions`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ index: String(index), completed: String(completed) }) }); if (!response.ok) throw new Error('Could not update action.'); }
export type LiveTranscript = { language: string; duration: number; segments: { start: number; end: number; text: string }[] };
export type LiveSummary = { summary: string; keyPoints: string[] };
export type LiveAnswer = { answer: string };
async function liveError(response: Response, fallback: string) { const body = await response.json().catch(() => ({ message: fallback })) as { message?: string }; return new Error(body.message ?? fallback); }
export async function transcribeLiveAudio(audio: Blob): Promise<LiveTranscript> { const body = new FormData(); body.append('audio', audio, 'live-chunk.wav'); const response = await fetch(`${API_BASE}/api/v1/live/transcribe`, { method: 'POST', body }); if (!response.ok) throw await liveError(response, 'Could not transcribe live audio.'); return response.json() as Promise<LiveTranscript>; }
export async function summarizeLive(transcript: string, period: string): Promise<LiveSummary> { const response = await fetch(`${API_BASE}/api/v1/live/summarize`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ transcript, period }) }); if (!response.ok) throw await liveError(response, 'Could not summarize this live excerpt.'); return response.json() as Promise<LiveSummary>; }
export async function askLive(transcript: string, question: string, scope: 'MEETING' | 'GLOBAL'): Promise<LiveAnswer> { const response = await fetch(`${API_BASE}/api/v1/live/ask`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ transcript, question, scope }) }); if (!response.ok) throw await liveError(response, 'Could not answer from the live meeting.'); return response.json() as Promise<LiveAnswer>; }
