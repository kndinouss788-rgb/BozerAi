import json
import wave

import pytest

from bozer_neural.voice.validate_voice_corpus import validate_voice_manifest


def _write_wav(path):
    with wave.open(str(path), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(16000)
        audio.writeframes(b"\x00\x00" * 8000)


def _manifest():
    return {
        "format": "bozer-tts-dataset-v1",
        "consent": {
            "speaker_id": "speaker_001",
            "status": "documented",
            "record_ref": "PRIVATE-CONSENT-RECORD",
            "scope": ["tts_training", "commercial_use", "derivative_voice_generation"],
        },
        "recordings": [
            {
                "recording_id": f"rec_{split}",
                "speaker_id": "speaker_001",
                "audio_path": f"{split}.wav",
                "text": f"Phrase de test pour {split}.",
                "session_id": f"session_{split}",
                "split": split,
            }
            for split in ("train", "validation", "test")
        ],
    }


def test_tts_manifest_validates_consent_audio_and_session_splits(tmp_path):
    for split in ("train", "validation", "test"):
        _write_wav(tmp_path / f"{split}.wav")
    manifest_path = tmp_path / "manifest.json"
    manifest_path.write_text(json.dumps(_manifest()), encoding="utf-8")

    report = validate_voice_manifest(manifest_path, tmp_path)

    assert report["recordings"] == 3
    assert report["sessions"] == 3
    assert report["recordings_by_split"] == {"train": 1, "validation": 1, "test": 1}
    assert report["audio_seconds"] == 1.5
    assert report["rights_and_consent_independently_verified"] is False


def test_tts_manifest_rejects_session_leakage_between_splits(tmp_path):
    for split in ("train", "validation", "test"):
        _write_wav(tmp_path / f"{split}.wav")
    manifest = _manifest()
    manifest["recordings"][1]["session_id"] = "session_train"
    manifest_path = tmp_path / "manifest.json"
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(ValueError, match="leaks between"):
        validate_voice_manifest(manifest_path, tmp_path)


def test_tts_manifest_rejects_missing_consent_scope(tmp_path):
    manifest = _manifest()
    manifest["consent"]["scope"] = ["tts_training"]
    manifest_path = tmp_path / "manifest.json"
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(ValueError, match="consent scope"):
        validate_voice_manifest(manifest_path, tmp_path)
