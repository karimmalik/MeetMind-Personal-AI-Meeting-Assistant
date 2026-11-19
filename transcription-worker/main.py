import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import av
from faster_whisper import WhisperModel


def main():
    parser = argparse.ArgumentParser(description="MeetMind local transcription worker")
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--model", default="base")
    parser.add_argument("--model-dir")
    parser.add_argument("--frames-dir")
    parser.add_argument("--frame-interval", type=int, default=60)
    args = parser.parse_args()

    model_source = args.model_dir if args.model_dir and Path(args.model_dir).is_dir() else args.model
    model = WhisperModel(model_source, device="cpu", compute_type="int8")
    segments, info = model.transcribe(args.input, beam_size=5, vad_filter=True)
    items = [{"start": round(segment.start, 3), "end": round(segment.end, 3), "text": segment.text.strip()} for segment in segments]
    payload = {"language": info.language, "duration": info.duration, "createdAt": datetime.now(timezone.utc).isoformat(), "segments": items}
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.frames_dir:
        frames_dir = Path(args.frames_dir)
        frames_dir.mkdir(parents=True, exist_ok=True)
        with av.open(args.input, mode="r") as container:
            if not container.streams.video:
                return
            video = container.streams.video[0]
            last_saved = -args.frame_interval
            for frame in container.decode(video):
                timestamp = float(frame.time or 0)
                if timestamp - last_saved >= args.frame_interval:
                    frame.to_image().save(frames_dir / f"frame-{int(timestamp * 1000):010d}.jpg", quality=84)
                    last_saved = timestamp


if __name__ == "__main__":
    main()
