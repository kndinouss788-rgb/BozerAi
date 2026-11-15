"""Validate a consented, single-speaker BOZER TTS dataset manifest and WAV files.

This validates metadata and audio format only. It does not verify a person's
identity, the authenticity of consent documents, or the legal effect of a license.
"""
import argparse
import json
import wave
from pathlib import Path

MANIFEST_FORMAT = "bozer-tts-dataset-v1"
REQUIRED_SCOPE = {"tts_training", "commercial_use", "derivative_voice_generation"}
ALLOWED_SAMPLE_RATES = {16000, 24000}
ALLOWED_SAMPLE_WIDTHS = {2, 3}


def validate_voice_manifest(manifest_path, dataset_root):
    manifest_path = Path(manifest_path)
    root = Path(dataset_root).resolve()
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(manifest, dict) or manifest.get("format") != MANIFEST_FORMAT:
        raise ValueError(f"manifest format must be {MANIFEST_FORMAT}")

    consent = manifest.get("consent")
    if not isinstance(consent, dict):
        raise ValueError("manifest needs a consent object")
    speaker_id = consent.get("speaker_id")
    if not isinstance(speaker_id, str) or not speaker_id.strip():
        raise ValueError("consent needs a pseudonymous speaker_id")
    if consent.get("status") != "documented":
        raise ValueError("speaker consent must be documented before dataset use")
    if not isinstance(consent.get("record_ref"), str) or not consent["record_ref"].strip():
        raise ValueError("consent needs a private record_ref")
    scope = consent.get("scope")
    if not isinstance(scope, list) or not REQUIRED_SCOPE.issubset(set(scope)):
        raise ValueError(f"consent scope must include {sorted(REQUIRED_SCOPE)}")

    recordings = manifest.get("recordings")
    if not isinstance(recordings, list) or not recordings:
        raise ValueError("manifest needs at least one recording")

    seen_ids = set()
    session_splits = {}
    split_counts = {"train": 0, "validation": 0, "test": 0}
    total_seconds = 0.0

    for index, item in enumerate(recordings, 1):
        if not isinstance(item, dict):
            raise ValueError(f"recording {index} must be an object")
        recording_id = item.get("recording_id")
        audio_path = item.get("audio_path")
        text = item.get("text")
        session_id = item.get("session_id")
        split = item.get("split")
        if not isinstance(recording_id, str) or not recording_id.strip():
            raise ValueError(f"recording {index} needs a recording_id")
        if recording_id in seen_ids:
            raise ValueError(f"duplicate recording_id: {recording_id}")
        seen_ids.add(recording_id)
        if item.get("speaker_id") != speaker_id:
            raise ValueError(f"recording {recording_id} is not from the declared single speaker")
        if not isinstance(text, str) or not text.strip():
            raise ValueError(f"recording {recording_id} needs its exact transcript")
        if not isinstance(session_id, str) or not session_id.strip():
            raise ValueError(f"recording {recording_id} needs a recording session_id")
        if split not in split_counts:
            raise ValueError(f"recording {recording_id} has invalid split {split!r}")
        previous_split = session_splits.get(session_id)
        if previous_split is not None and previous_split != split:
            raise ValueError(
                f"recording session {session_id!r} leaks between {previous_split} and {split}"
            )
        session_splits[session_id] = split

        if not isinstance(audio_path, str) or not audio_path.strip():
            raise ValueError(f"recording {recording_id} needs audio_path")
        path = (root / audio_path).resolve()
        try:
            path.relative_to(root)
        except ValueError as exc:
            raise ValueError(f"recording {recording_id} audio_path escapes dataset root") from exc
        if not path.is_file() or path.suffix.lower() != ".wav":
            raise ValueError(f"recording {recording_id} must reference an existing WAV file")

        try:
            with wave.open(str(path), "rb") as audio:
                channels = audio.getnchannels()
                sample_width = audio.getsampwidth()
                sample_rate = audio.getframerate()
                frames = audio.getnframes()
                compression = audio.getcomptype()
        except (wave.Error, EOFError) as exc:
            raise ValueError(f"recording {recording_id} is not a readable PCM WAV") from exc

        if channels != 1:
            raise ValueError(f"recording {recording_id} must be mono")
        if sample_width not in ALLOWED_SAMPLE_WIDTHS:
            raise ValueError(f"recording {recording_id} must use 16-bit or 24-bit PCM")
        if sample_rate not in ALLOWED_SAMPLE_RATES:
            raise ValueError(f"recording {recording_id} must be 16 kHz or 24 kHz")
        if compression != "NONE" or frames <= 0:
            raise ValueError(f"recording {recording_id} must contain uncompressed PCM audio")
        duration = frames / sample_rate
        if not 0.5 <= duration <= 20.0:
            raise ValueError(f"recording {recording_id} duration must be between 0.5 and 20 seconds")

        split_counts[split] += 1
        total_seconds += duration

    if any(count == 0 for count in split_counts.values()):
        raise ValueError("train, validation and test must each contain recordings")

    return {
        "format": MANIFEST_FORMAT,
        "speaker_id": speaker_id,
        "recordings": len(recordings),
        "sessions": len(session_splits),
        "recordings_by_split": split_counts,
        "audio_seconds": round(total_seconds, 3),
        "audio_hours": round(total_seconds / 3600, 6),
        "audio_constraints": {
            "channels": 1,
            "sample_rates_hz": sorted(ALLOWED_SAMPLE_RATES),
            "sample_width_bits": [16, 24],
            "duration_seconds": [0.5, 20.0],
        },
        "consent_metadata_present": True,
        "rights_and_consent_independently_verified": False,
    }


def main():
    parser = argparse.ArgumentParser(description="Validate BOZER TTS recordings and consent metadata.")
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--dataset-dir", required=True)
    args = parser.parse_args()
    print(json.dumps(
        validate_voice_manifest(args.manifest, args.dataset_dir),
        ensure_ascii=False,
        indent=2,
    ))


if __name__ == "__main__":
    main()
