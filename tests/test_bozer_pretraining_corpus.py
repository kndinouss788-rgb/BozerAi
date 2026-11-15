import json

import pytest

from bozer_neural.prepare_pretraining_corpus import prepare_corpus


def _paragraph(index):
    return (
        f"Document numéro {index}. " +
        ("Ce passage décrit une idée complète, avec des détails utiles et une explication. " * 3)
    )


def _make_source_documents(source):
    source.mkdir()
    for index in range(160):
        content = _paragraph(index)
        if index == 1:
            content += "\n\n" + _paragraph(0)  # cross-document duplicate is removed
        if index == 3:
            content += (
                "\n\nContact: personne@example.org www.example.org +226 70 12 34 56 "
                + ("Texte additionnel pour dépasser la longueur minimale. " * 3)
            )
        (source / f"document-{index:03d}.txt").write_text(content, encoding="utf-8")


def test_prepare_corpus_is_deterministic_and_splits_whole_source_documents(tmp_path):
    source = tmp_path / "sources"
    _make_source_documents(source)
    out1, out2 = tmp_path / "out1", tmp_path / "out2"
    first = prepare_corpus(source, out1)
    second = prepare_corpus(source, out2)

    assert first["split_blocks"] == second["split_blocks"]
    assert first["split_source_documents"] == second["split_source_documents"]
    assert first["output_sha256"] == second["output_sha256"]
    assert first["counts"]["duplicate"] == 1
    assert first["format"] == "bozer-pretraining-corpus-v2"
    assert first["split_method"] == "sha256_source_id_mod_100_source_document_grouping"
    assert first["training_ready"] is False

    owner = {}
    for source_record in first["sources"]:
        split = source_record.get("split")
        if split:
            assert source_record["source_file"] not in owner
            owner[source_record["source_file"]] = split
    assert len(owner) == 160
    assert set(owner.values()) == {"train", "validation", "test"}

    contents = {
        name: (out1 / f"{name}.txt").read_text(encoding="utf-8")
        for name in ("train", "validation", "test")
    }
    blocks_by_split = {
        name: set(value for value in content.split("\n\n") if value)
        for name, content in contents.items()
    }
    assert not (blocks_by_split["train"] & blocks_by_split["validation"])
    assert not (blocks_by_split["train"] & blocks_by_split["test"])
    assert not (blocks_by_split["validation"] & blocks_by_split["test"])
    all_content = "".join(contents.values())
    assert "personne@example.org" not in all_content
    assert "www.example.org" not in all_content
    assert "[EMAIL]" in all_content
    assert "[PHONE]" in all_content
    assert json.loads((out1 / "report.json").read_text())["format"] == "bozer-pretraining-corpus-v2"


def test_prepare_corpus_marks_fully_documented_authorized_sources(tmp_path):
    source = tmp_path / "sources"
    _make_source_documents(source)
    entries = []
    for index in range(160):
        entries.append({
            "path": f"document-{index:03d}.txt",
            "source_id": f"document-{index:03d}",
            "title": f"Document {index}",
            "author": "Auteur autorisé",
            "publisher": "Source de test",
            "source_url": f"https://example.org/{index}",
            "license": "CC BY 4.0",
            "license_url": "https://creativecommons.org/licenses/by/4.0/",
            "retrieved_at": "2026-11-15",
            "allowed_use": True,
            "attribution": "Auteur autorisé, CC BY 4.0",
        })
    manifest = tmp_path / "manifest.json"
    manifest.write_text(json.dumps({
        "format": "bozer-source-manifest-v1",
        "sources": entries,
    }), encoding="utf-8")
    report = prepare_corpus(source, tmp_path / "out", source_manifest=manifest)
    assert report["training_ready"] is True
    assert all(
        item["rights_status"] == "owner_declared_authorized"
        for item in report["sources"] if item["unique_blocks"]
    )


def test_prepare_corpus_rejects_incomplete_source_manifest(tmp_path):
    source = tmp_path / "sources"
    _make_source_documents(source)
    manifest = tmp_path / "manifest.json"
    manifest.write_text(json.dumps({
        "format": "bozer-source-manifest-v1",
        "sources": [{
            "path": "document-000.txt",
            "source_id": "document-000",
            "license": "CC BY 4.0",
            "license_url": "https://creativecommons.org/licenses/by/4.0/",
            "allowed_use": True,
        }],
    }), encoding="utf-8")
    with pytest.raises(ValueError, match="cover every .txt file"):
        prepare_corpus(source, tmp_path / "out", source_manifest=manifest)


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
