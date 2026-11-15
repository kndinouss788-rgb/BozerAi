# BOZER TTS — dataset contract

Status: validator and data contract only. No speaker recordings or trained TTS checkpoint are present in this repository.

## Consent and use

Use one consistent adult speaker who has explicitly agreed to:
- TTS model training;
- commercial use in BOZER;
- generation and distribution of synthetic speech derived from the recordings.

Keep the signed consent document private. The public or working manifest stores only a private reference identifier, not the consent document or personal contact details. Do not scrape recordings, clone a public figure, imitate an actor or fictional character, or mix speakers without a deliberate multi-speaker design.

## Audio format

- WAV, uncompressed PCM, mono.
- 16 kHz or 24 kHz.
- 16-bit or 24-bit samples.
- 0.5 to 20 seconds per utterance.
- Exact transcript, including punctuation and numerals as spoken.
- Use separate recording sessions for train, validation and test. Never split takes from one session across partitions.

## Manifest format

The validator is run with a manifest JSON file and an audio folder. The required manifest has the following structure:

    {
      "format": "bozer-tts-dataset-v1",
      "consent": {
        "speaker_id": "speaker_001",
        "status": "documented",
        "record_ref": "PRIVATE-CONSENT-RECORD",
        "scope": ["tts_training", "commercial_use", "derivative_voice_generation"]
      },
      "recordings": [
        {
          "recording_id": "rec_0001",
          "speaker_id": "speaker_001",
          "audio_path": "train/rec_0001.wav",
          "text": "Oui, je t'écoute.",
          "session_id": "session_train_01",
          "split": "train"
        }
      ]
    }

The example is a schema illustration, not real consent or a valid complete corpus: actual manifests must include recordings in all three splits. The validator checks format, declared consent scope, single-speaker consistency, session separation, WAV properties, and safe file paths. It does not independently authenticate consent or license claims.

Run it with:
- python -m bozer_neural.voice.validate_voice_corpus --manifest manifest.json --dataset-dir audio-folder

## Next gates

1. Obtain a consistent, authorized speaker and record a varied French dataset.
2. Correct transcripts and pronunciation; keep session-level held-out splits.
3. Compare local TTS model and runtime licenses before selecting an architecture.
4. Train only when the dataset passes validation and lawful use is documented.
5. Measure naturalness, intelligibility, latency, memory, battery, and interruption on a real Android phone.
