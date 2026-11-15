import pytest

from bozer_neural.memory import MemoryRecord, MemoryStore
from bozer_neural.planning import ActionKind, ActionPlan, PlannedAction
from bozer_neural.language.language_router import choose_language
from bozer_neural.voice.pronunciation import normalize_for_speech
from bozer_neural.voice.tts_contract import SpeechRequest
from bozer_neural.language.moore_french import MooreFrenchTranslator
from bozer_neural.language.translation_pipeline import TranslationPipeline, TranslationRequest


def test_memory_lifecycle_and_privacy_filter():
    store = MemoryStore()
    ordinary = MemoryRecord.create("appeler maman", kind="task", source="user")
    private = MemoryRecord.create("private note", kind="note", source="user",
                                  sensitivity="private")
    store.add(ordinary)
    store.add(private)
    assert [r.id for r in store.recall()] == [ordinary.id]
    assert len(store.recall(include_private=True)) == 2
    assert store.forget(ordinary.id)
    assert not store.forget(ordinary.id)


def test_memory_rejects_invalid_confidence():
    with pytest.raises(ValueError):
        MemoryRecord.create("x", kind="fact", source="user", confidence=1.5)


def test_action_plan_requires_capability_gate_for_phone_actions():
    with pytest.raises(ValueError):
        ActionPlan("send", (PlannedAction(ActionKind.SEND_MESSAGE),), "proposal", 0.9)
    plan = ActionPlan("send", (PlannedAction(
        ActionKind.SEND_MESSAGE, {"body": "Salut"},
        requires_confirmation=True, required_capability="sms"
    ),), "proposal", 0.9)
    assert plan.actions[0].required_capability == "sms"


def test_language_router_respects_explicit_moore_choice():
    decision = choose_language("Ne y windiga", explicit="mos")
    assert decision.language == "mos"
    assert decision.confidence == 1.0


def test_language_router_does_not_claim_confident_auto_detection():
    decision = choose_language("bonjour")
    assert decision.confidence < 0.5


def test_speech_normalization_preserves_moore_diacritics():
    assert normalize_for_speech("  sãaga  ", "mos-BF") == "sãaga"


def test_speech_request_validates_rate():
    with pytest.raises(ValueError):
        SpeechRequest("Bonjour", rate=2.0)


def test_translation_pipeline_does_not_invent_unknown_phrase():
    translator = MooreFrenchTranslator([])
    pipeline = TranslationPipeline(translator)
    response = pipeline.translate(TranslationRequest("phrase inconnue", "fr", "mos"))
    assert response.result.status == "not_found"
    assert response.result.translated_text is None
    assert response.requires_human_review
