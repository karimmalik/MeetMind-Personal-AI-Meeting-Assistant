import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';
import './transcript.css';
import './insights.css';
import './questions.css';
import './reliability.css';
import './settings.css';
import './frames.css';
import './typography.css';
import './live.css';
import './live-chat.css';
import './meeting-conversation.css';
import { App } from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode><App /></StrictMode>
);
