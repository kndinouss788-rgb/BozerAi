import json
from pathlib import Path


def test_bozer_voice_profile_is_original_masculine_french_first_design_not_a_model():
    profile_path = Path("bozer_neural/voice/voice_profile.json")
    profile = json.loads(profile_path.read_text(encoding="utf-8"))

    assert profile["status"] == "design_specification_not_a_trained_voice"
    assert profile["language_priority"] == ["fr-FR"]
    assert profile["voice_identity"]["presentation"] == "masculine"
    assert profile["voice_identity"]["science_fiction_inspiration"].endswith(
        "no_voice_or_character_imitation"
    )
    assert profile["identity_and_safety"]["imitate_existing_fictional_character_voice"] is False
    assert profile["runtime"]["offline_target"] is True
    assert profile["training"]["requires_lawful_consenting_speaker_data"] is True
