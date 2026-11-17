"""Offline unit tests for corpus categorization, splitting and gzip readers."""
import gzip
import json
import tempfile
import unittest
from pathlib import Path

from bozer_neural.data import is_dialogue_path, read_records
from bozer_neural.prepare_100k_corpus import CATEGORIES, category, split_records
from bozer_neural.validate_corpus import validate_splits


def record(index, category_name):
    return {
        "category": category_name,
        "user": "Question unique numéro %s : explique ce sujet en détail." % index,
        "assistant": "Réponse structurée et unique numéro %s, avec les éléments essentiels." % index,
        "language": "fr",
        "source": "fixture",
        "source_license": "test",
        "source_id": str(index),
        "source_group_id": "fixture:" + str(index),
    }


def write_gzip(path, records):
    with gzip.open(path, "wt", encoding="utf-8") as handle:
        for item in records:
            handle.write(json.dumps(item, ensure_ascii=False) + "\n")


class TestBozer100kCorpus(unittest.TestCase):
    def test_classifier_categories(self):
        self.assertEqual(
            category("How do I keep my password secure?", "Use a password manager."),
            "securite_confidentialite_honnetete",
        )
        self.assertEqual(
            category("Write a Python function", "Here is the code."),
            "utilisation_outils_verification",
        )
        self.assertEqual(
            category("Plan a weekly project schedule", "Start with milestones."),
            "planification_missions",
        )
        self.assertEqual(
            category("Solve this equation", "The answer is four."),
            "raisonnement_resolution",
        )
        self.assertEqual(
            category("What is photosynthesis?", "It is a biological process."),
            "comprehension_demande",
        )

    def test_gzip_and_sharded_dialogue_reading(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            train_dir, validation_dir, test_dir = (
                root / "train", root / "validation", root / "test"
            )
            for directory in (train_dir, validation_dir, test_dir):
                directory.mkdir()
            cats = list(CATEGORIES)
            train = [record(i, cats[i % 6]) for i in range(120)]
            validation = [record(i + 1000, cats[i % 6]) for i in range(30)]
            test = [record(i + 2000, cats[i % 6]) for i in range(30)]
            write_gzip(train_dir / "part-00000.jsonl.gz", train)
            write_gzip(validation_dir / "part-00000.jsonl.gz", validation)
            write_gzip(test_dir / "part-00000.jsonl.gz", test)
            self.assertTrue(is_dialogue_path(train_dir))
            self.assertTrue(is_dialogue_path(root / "sample.jsonl.gz"))
            self.assertFalse(is_dialogue_path(root / "sample.txt"))
            self.assertEqual(len(read_records(train_dir)), 120)
            report = validate_splits(train_dir, validation_dir, test_dir)
            self.assertEqual(sum(report["train"].values()), 120)
            self.assertEqual(sum(report["validation"].values()), 30)
            self.assertEqual(sum(report["test"].values()), 30)

    def test_split_sizes_categories_and_group_separation(self):
        records = [record(i, CATEGORIES[i % 6]) for i in range(600)]
        parts = split_records(records, 540, 30, 30)
        self.assertEqual(
            [len(parts[name]) for name in ("train", "validation", "test")],
            [540, 30, 30],
        )
        groups = [
            {item["source_group_id"] for item in parts[name]}
            for name in ("train", "validation", "test")
        ]
        self.assertFalse(groups[0] & groups[1])
        self.assertFalse(groups[0] & groups[2])
        self.assertFalse(groups[1] & groups[2])
        for split in parts.values():
            self.assertEqual({item["category"] for item in split}, set(CATEGORIES))


if __name__ == "__main__":
    unittest.main()
