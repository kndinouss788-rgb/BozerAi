import pytest
import torch

from bozer_neural.config_1b import Bozer1BConfig
from bozer_neural.generation import (
    GenerationConfig, filter_next_token_logits, generate, sample_next_token,
)
from bozer_neural.language.dictionary import MooreFrenchDictionary
from bozer_neural.language.evaluation import TranslationExample, evaluate_translator
from bozer_neural.language.morphology import MorphologyRule, RuleBasedMorphology
from bozer_neural.language.moore_french import MooreFrenchTranslator
from bozer_neural.voice.frontend import SpeechTextFrontend
from bozer_neural.voice.pronunciation_lexicon import (
    PronunciationEntry, PronunciationLexicon,
)
from bozer_neural.voice.tts_contract import AudioChunk, SpeechController, SpeechRequest


def tiny_model():
    config = Bozer1BConfig(
        vocab_size=300, context_length=16, d_model=32, n_layers=1,
        n_query_heads=4, n_kv_heads=2, head_dim=8, ffn_dim=64,
    )
    return __import__("bozer_neural.model_1b", fromlist=["BozerNeural1B"]).BozerNeural1B(config)


def test_generation_applies_active_vocab_and_returns_expected_shape():
    torch.manual_seed(17)
    model = tiny_model()
    prompt = torch.tensor([[1, 7, 8]], dtype=torch.long)
    config = GenerationConfig(
        max_new_tokens=3, min_new_tokens=3, temperature=0,
        top_k=1, top_p=1, eos_token_id=None, active_vocab_size=32,
    )
    result = generate(model, prompt, config)
    assert result.shape == (1, 6)
    assert int(result.max()) < 32


def test_generation_restores_model_training_mode():
    model = tiny_model()
    model.train()
    prompt = torch.tensor([[1, 7]], dtype=torch.long)
    generate(model, prompt, GenerationConfig(
        max_new_tokens=1, temperature=0, top_k=1, eos_token_id=None,
        active_vocab_size=24,
    ))
    assert model.training


def test_generation_config_rejects_invalid_sampling_parameters():
    with pytest.raises(ValueError):
        GenerationConfig(top_p=0)
    with pytest.raises(ValueError):
        GenerationConfig(repetition_penalty=0.5)


def test_no_repeat_unigram_blocks_used_tokens():
    logits = torch.tensor([1.0, 2.0, 3.0, 4.0])
    filtered = filter_next_token_logits(
        logits, [3, 1], GenerationConfig(
            max_new_tokens=2, top_k=None, top_p=1,
            no_repeat_ngram_size=1, eos_token_id=None,
        ), generated_count=0,
    )
    assert torch.isneginf(filtered[3])
    assert torch.isneginf(filtered[1])
    assert torch.isfinite(filtered[2])


def test_top_p_keeps_the_highest_probability_token():
    logits = torch.tensor([5.0, 4.0, 1.0])
    filtered = filter_next_token_logits(
        logits, [], GenerationConfig(
            top_k=None, top_p=0.5, eos_token_id=None,
        ), generated_count=0,
    )
    assert torch.isfinite(filtered[0])
    assert torch.isneginf(filtered[1:]).all()


def test_dictionary_exact_and_search_hide_candidates_by_default():
    entries = [
        {"id": "v1", "french": "bonjour", "moore": "ne y yibeoogo",
         "status": "validated", "source": "contribution-1",
         "reviewed_by": "native-reviewer", "category": "phrase"},
        {"id": "c1", "french": "bonsoir", "moore": "candidate form",
         "status": "candidate", "category": "phrase"},
    ]
    dictionary = MooreFrenchDictionary(entries)
    assert len(dictionary.exact("BONJOUR", "fr")) == 1
    assert dictionary.exact("bonsoir", "fr") == ()
    assert dictionary.search("bon", "fr")[0].entry_id == "v1"
    candidates = MooreFrenchDictionary(entries, include_candidates=True)
    assert len(candidates.exact("bonsoir", "fr")) == 1


def test_dictionary_rejects_duplicate_ids():
    entry = {"id": "same", "french": "a", "moore": "b", "status": "candidate"}
    with pytest.raises(ValueError, match="duplicate"):
        MooreFrenchDictionary([entry, entry])


def test_morphology_only_applies_validated_documented_rules():
    rules = [
        MorphologyRule("mos-sfx-1", "mos", "suffix", "ba", "feature-x",
                       "field-note-1", "validated", "reviewer-1"),
        MorphologyRule("mos-sfx-c", "mos", "suffix", "se", "unreviewed",
                       "pending-note", "candidate"),
    ]
    engine = RuleBasedMorphology(rules)
    analyses = engine.analyze("wordba", "mos")
    assert len(analyses) == 1
    assert analyses[0].stem == "word"
    assert analyses[0].feature == "feature-x"


def test_morphology_refuses_validated_rule_without_reviewer():
    with pytest.raises(ValueError, match="reviewer"):
        MorphologyRule("r1", "mos", "prefix", "a", "feature", "source",
                       "validated")


def test_translation_evaluation_reports_accuracy_and_coverage():
    entries = [{
        "id": "v1", "french": "bonjour", "moore": "ne y yibeoogo",
        "status": "validated", "source": "source-1", "reviewed_by": "native-1",
    }]
    translator = MooreFrenchTranslator(entries)
    report = evaluate_translator(translator, [
        TranslationExample("bonjour", "fr", "mos", "ne y yibeoogo"),
        TranslationExample("unknown", "fr", "mos", "unknown target"),
    ])
    assert report["examples"] == 2
    assert report["exact_matches"] == 1
    assert report["coverage"] == 0.5


def test_speech_frontend_preserves_diacritics_and_chunks_text():
    frontend = SpeechTextFrontend(max_characters=24)
    segments = frontend.prepare("sãaga, bonjour. Comment vas-tu aujourd'hui ?", "mos-BF")
    assert segments
    combined = " ".join(item.text for item in segments)
    assert "sãaga" in combined
    assert combined.endswith("?")
    assert all(len(item.text) <= 24 for item in segments)


def test_pronunciation_lexicon_hides_candidate_entries_by_default():
    reviewed = PronunciationEntry("sãaga", "mos-BF", "phoneme-sequence",
                                   "field-note", "validated", "reviewer")
    candidate = PronunciationEntry("word", "mos-BF", "pending",
                                    "pending-note", "candidate")
    lexicon = PronunciationLexicon([reviewed, candidate])
    assert lexicon.lookup("SÃAGA", "mos-BF") == (reviewed,)
    assert lexicon.lookup("word", "mos-BF") == ()


def test_audio_chunk_validates_pcm_shape_and_rate():
    assert AudioChunk(b"\x00\x00\x01\x00", 24000).sample_rate == 24000
    with pytest.raises(ValueError, match="even"):
        AudioChunk(b"\x00", 24000)
    with pytest.raises(ValueError, match="sample rate"):
        AudioChunk(b"\x00\x00", 12345)


def test_speech_controller_can_cancel_streamed_output():
    class Backend:
        def synthesize(self, request):
            yield AudioChunk(b"\x00\x00", 24000)
            yield AudioChunk(b"\x01\x00", 24000, final=True)

    controller = SpeechController(Backend())
    stream = controller.stream(SpeechRequest("Bonjour"))
    first = next(stream)
    assert first.pcm == b"\x00\x00"
    controller.cancel()
    with pytest.raises(StopIteration):
        next(stream)
