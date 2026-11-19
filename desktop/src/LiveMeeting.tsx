import { FormEvent, useEffect, useRef, useState } from 'react';
import { askLive, createMeeting, summarizeLive, transcribeLiveAudio } from './services/backend';

type Segment = { start: number; text: string };
type RecordingState = 'setup' | 'recording' | 'saving';
type AskScope = 'MEETING' | 'GLOBAL';
type LiveMessage =
  | { id: string; type: 'summary'; period: string; summary: string; keyPoints: string[] }
  | { id: string; type: 'answer'; scope: AskScope; question: string; answer: string };

function clock(seconds: number) {
  const value = Math.max(0, Math.floor(seconds));
  return `${String(Math.floor(value / 3600)).padStart(2, '0')}:${String(Math.floor(value / 60) % 60).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
}

function wavFromBase64(base64: string) {
  const binary = window.atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
  return new Blob([bytes], { type: 'audio/wav' });
}

function LiveConversation({ messages }: { messages: LiveMessage[] }) {
  if (!messages.length) return null;
  return <section className="live-conversation"><p className="eyebrow">LIVE NOTES</p><div className="live-chat">{messages.map((message) => message.type === 'summary' ? <article className="chat-bubble assistant" key={message.id}><span className="chat-label">Summary · last {message.period}</span><strong>{message.summary}</strong>{message.keyPoints.map((point, index) => <p key={index}>• {point}</p>)}</article> : <article className="chat-pair" key={message.id}><div className="chat-bubble user"><span className="chat-label">You · {message.scope === 'GLOBAL' ? 'Broader knowledge' : 'Meeting context'}</span><p>{message.question}</p></div><div className="chat-bubble assistant"><span className="chat-label">RIMeet {message.scope === 'GLOBAL' ? '· Broader knowledge, not verified by this meeting' : '· Based on this meeting'}</span><p>{message.answer}</p></div></article>)}</div></section>;
}

export function LiveMeeting({ onSaved, onRecordingChange }: { onSaved: () => void; onRecordingChange: (active: boolean) => void }) {
  const [state, setState] = useState<RecordingState>('setup');
  const [title, setTitle] = useState('Live meeting');
  const [segments, setSegments] = useState<Segment[]>([]);
  const [startedAt, setStartedAt] = useState<number | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const [transcribing, setTranscribing] = useState(false);
  const [messages, setMessages] = useState<LiveMessage[]>([]);
  const [summarizing, setSummarizing] = useState(false);
  const [question, setQuestion] = useState('');
  const [askScope, setAskScope] = useState<AskScope>('MEETING');
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState('');
  const captureActiveRef = useRef(false);
  const chunkQueueRef = useRef(Promise.resolve());
  const startTimeRef = useRef(0);
  const drainingRef = useRef(false);

  const queueChunk = (chunk: Blob) => {
    if (chunk.size === 0) return;
    chunkQueueRef.current = chunkQueueRef.current.then(async () => {
      setTranscribing(true);
      try {
        const result = await transcribeLiveAudio(chunk);
        const receivedAt = (Date.now() - startTimeRef.current) / 1000;
        setSegments((current) => [...current, ...result.segments.map((segment) => ({ start: Math.max(0, receivedAt - result.duration + segment.start), text: segment.text }))]);
      } catch (reason) { setError(reason instanceof Error ? reason.message : 'A live audio chunk could not be transcribed.'); }
      finally { setTranscribing(false); }
    });
  };

  const drainNativeChunks = async () => {
    if (drainingRef.current) return;
    const bridge = window.meetmind;
    if (!bridge) throw new Error('RIMeet desktop audio bridge is unavailable.');
    drainingRef.current = true;
    try {
      while (true) {
        const encoded = await bridge.takeNativeLiveChunk();
        if (!encoded) break;
        queueChunk(wavFromBase64(encoded));
      }
    } finally { drainingRef.current = false; }
  };

  useEffect(() => () => { if (captureActiveRef.current) void window.meetmind?.disposeNativeLiveAudio(); }, []);

  useEffect(() => {
    if (state !== 'recording' || !startedAt) return;
    const timer = window.setInterval(() => setElapsed((Date.now() - startedAt) / 1000), 500);
    return () => window.clearInterval(timer);
  }, [state, startedAt]);

  useEffect(() => {
    const active = state === 'recording' || state === 'saving';
    onRecordingChange(active);
    if (!active) return;
    const warnBeforeLeaving = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warnBeforeLeaving);
    return () => { window.removeEventListener('beforeunload', warnBeforeLeaving); };
  }, [state, onRecordingChange]);

  useEffect(() => {
    if (state !== 'recording') return;
    const poll = () => void drainNativeChunks().catch((reason) => setError(reason instanceof Error ? reason.message : 'Could not read the live audio stream.'));
    poll();
    const timer = window.setInterval(poll, 1000);
    return () => window.clearInterval(timer);
  }, [state]);

  const transcriptText = (windowSeconds?: number) => {
    const cutoff = windowSeconds ? Math.max(0, elapsed - windowSeconds) : 0;
    return segments.filter((segment) => segment.start >= cutoff).map((segment) => `[${clock(segment.start)}] ${segment.text}`).join('\n');
  };

  const start = async () => {
    setError(''); setMessages([]); setSegments([]); chunkQueueRef.current = Promise.resolve();
    try {
      const bridge = window.meetmind;
      if (!bridge) throw new Error('RIMeet desktop audio bridge is unavailable.');
      await bridge.startNativeLiveAudio();
      captureActiveRef.current = true;
      startTimeRef.current = Date.now(); setStartedAt(startTimeRef.current); setElapsed(0); setState('recording');
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not start native Windows audio capture.'); }
  };

  const summarize = async (windowSeconds: number) => {
    const transcript = transcriptText(windowSeconds);
    if (!transcript) { setError('Wait for some transcript before summarizing.'); return; }
    setError(''); setSummarizing(true);
    try { const period = `${Math.floor(windowSeconds / 60)} minute${windowSeconds > 60 ? 's' : ''}`; const result = await summarizeLive(transcript, period); setMessages((current) => [...current, { id: crypto.randomUUID(), type: 'summary', period, summary: result.summary, keyPoints: result.keyPoints }]); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not summarize the live meeting.'); }
    finally { setSummarizing(false); }
  };

  const ask = async (event: FormEvent) => {
    event.preventDefault(); if (!question.trim()) return;
    const transcript = transcriptText(); if (!transcript && askScope === 'MEETING') { setError('Wait for some transcript before asking a meeting-context question.'); return; }
    setAsking(true); setError('');
    try { const result = await askLive(transcript, question, askScope); setMessages((current) => [...current, { id: crypto.randomUUID(), type: 'answer', scope: askScope, question, answer: result.answer }]); setQuestion(''); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not answer this live question.'); }
    finally { setAsking(false); }
  };

  const stop = async () => {
    setState('saving');
    try {
      const bridge = window.meetmind;
      if (!bridge) throw new Error('RIMeet desktop audio bridge is unavailable.');
      const finalAudio = await bridge.stopNativeLiveAudio();
      await drainNativeChunks();
      await chunkQueueRef.current;
      const recording = new File([wavFromBase64(finalAudio)], `${title.trim() || 'live-meeting'}.wav`, { type: 'audio/wav' });
      if (!recording.size) throw new Error('No audio was captured.');
      await createMeeting(recording, title.trim() || 'Live meeting', 'AUDIO_ONLY');
      await bridge.disposeNativeLiveAudio();
      captureActiveRef.current = false;
      setState('setup'); setStartedAt(null); onSaved();
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not save this live meeting.'); setState('recording'); }
  };

  if (state === 'setup') return <section className="live-page"><div className="live-intro"><p className="eyebrow">RIMEET LIVE</p><h2>Start a live meeting.</h2><p>Capture the Windows default microphone and all audio playing on this PC. RIMeet uses native Windows loopback, so it does not need screen-sharing access.</p></div><section className="live-setup"><label className="field-label" htmlFor="live-title">MEETING TITLE</label><input id="live-title" value={title} onChange={(event) => setTitle(event.target.value)} /><div className="system-audio-toggle"><span><strong>Windows default microphone + system audio</strong><small>Includes your voice and other participants from Zoom, Meet, Teams, or any audio playing on this PC. Change the default mic in Windows Sound settings if needed.</small></span></div><button className="primary live-start" onClick={() => void start()}>● Start live meeting</button>{error && <p className="form-error">{error}</p>}</section></section>;

  return <section className="live-page live-running"><div className="live-header"><div><p className="eyebrow">RIMEET LIVE</p><h2>{title || 'Live meeting'}</h2><div className="recording-state"><span /> Recording · {clock(elapsed)} {transcribing && <em>Transcribing latest audio…</em>}</div></div><div className="source-chip">🎙 Windows mic + system audio</div></div><div className="live-grid"><section className="live-transcript"><div className="section-heading"><div><p className="eyebrow">LIVE TRANSCRIPT</p><h3>Conversation so far</h3></div></div>{segments.length ? <div className="live-segments">{segments.map((segment, index) => <div key={`${segment.start}-${index}`}><time>{clock(segment.start)}</time><p>{segment.text}</p></div>)}</div> : <div className="empty-state"><strong>Listening…</strong><span>Your transcript will appear after the first audio segment.</span></div>}</section><aside className="live-tools"><p className="eyebrow">CATCH UP</p><h3>What did I miss?</h3><div className="live-summary-actions"><button disabled={summarizing} onClick={() => void summarize(60)}>{summarizing ? 'Summarizing…' : 'Summarize last 1 min'}</button><button disabled={summarizing} onClick={() => void summarize(300)}>Summarize last 5 min</button></div><section className="live-ask"><p className="eyebrow">ASK RIMEET</p><div className="ask-scope"><button type="button" className={askScope === 'MEETING' ? 'selected' : ''} onClick={() => setAskScope('MEETING')}>Meeting context</button><button type="button" className={askScope === 'GLOBAL' ? 'selected' : ''} onClick={() => setAskScope('GLOBAL')}>Broader knowledge</button></div><small>{askScope === 'MEETING' ? 'Answers use only what was said in this meeting.' : 'Answers can use general knowledge and will be marked as not verified by this meeting.'}</small><form onSubmit={ask}><input value={question} onChange={(event) => setQuestion(event.target.value)} placeholder={askScope === 'GLOBAL' ? 'e.g. What is an MR?' : 'What was decided?'} /><button disabled={asking}>{asking ? '…' : 'Ask'}</button></form></section><LiveConversation messages={messages} /><button className="stop-live" disabled={state === 'saving'} onClick={() => void stop()}>{state === 'saving' ? 'Saving meeting…' : '■ Stop & save meeting'}</button>{error && <p className="form-error">{error}</p>}</aside></div></section>;
}
