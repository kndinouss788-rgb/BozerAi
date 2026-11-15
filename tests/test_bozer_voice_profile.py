import json
from pathlib import Path


def test_bozer_voice_profile_is_original_masculine_french_first_design_not_a_model():
    profile_path = Path("bozer_neural/voice/voice_profile.json")
    profile = json.loads(profile_path.read_text(encoding="utf-8"))

    assert profile["status"] == "design_specification_not_a_trained_voice"
    assert "fr-FR" in profile["language_priority"]
    assert profile["voice"]["gender_presentation"] == "masculine"
    assert profile["identity_safety"]["no_character_or_actor_imitation"] is True
    assert profile["runtime"]["offline_target"] is True
