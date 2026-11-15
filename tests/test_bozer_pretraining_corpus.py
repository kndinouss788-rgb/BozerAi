import json
from pathlib import Path

import pytest

from bozer_neural.prepare_pretraining_corpus import prepare_corpus


def _paragraph(index):
    return (
        f"Document numéro {index}. " +
        ("Ce passage décrit une idée complète, avec des détails utiles et une explication. " * 3)
    )


def test_prepare_corpus_is_deterministic_deduplicated_and_disjoint(tmp_path):
    source = tmp_path / "sources"
    source.mkdir()
    blocks = [_paragraph(i) for i in range(100)]
    # Add an exact duplicate and obvious personal data to check redaction.
    (source / "one.txt").write_text(
        "\\n\\n".join(blocks + [blocks[0]]) +
        "\\n\\nContact: personne@example.org www.example.org +226 70 12 34 56 " +
        ("Texte additionnel pour dépasser la longueur minimale. " * 3),
        encoding="utf-8",
    )
    out1, out2 = tmp_path / "out1", tmp_path / "out2"
    first = prepare_corpus(source, out1)
    second = prepare_corpus(source, out2)

    assert first["split_blocks"] == second["split_blocks"]
    assert first["output_sha256"] == second["output_sha256"]
    assert first["counts"]["duplicate"] == 1
    assert all(first["split_blocks"][name] > 0 for name in ("train", "validation", "test"))

    contents = {
        name: (out1 / f"{name}.txt").read_text(encoding="utf-8")
        for name in ("train", "validation", "test")
    }
    assert not (set(contents["train"].split("\\n\\n")) &
                set(contents["validation"].split("\\n\\n")))
    assert "personne@example.org" not in "".join(contents.values())
    assert "www.example.org" not in "".join(contents.values())
    assert "[EMAIL]" in "".join(contents.values())
    assert json.loads((out1 / "report.json").read_text())["format"] == "bozer-pretraining-corpus-v1"


def test_prepare_corpus_rejects_missing_sources(tmp_path):
    empty = tmp_path / "empty"
    empty.mkdir()
    with pytest.raises(ValueError, match="no .txt"):
        prepare_corpus(empty, tmp_path / "out")


def test_prepare_corpus_rejects_invalid_split_percentages(tmp_path):
    source = tmp_path / "sources"
    source.mkdir()
    (source / "source.txt").write_text(_paragraph(1), encoding="utf-8")
    with pytest.raises(ValueError, match="leave room for test"):
        prepare_corpus(source, tmp_path / "out", train_percent=95, validation_percent=5)
