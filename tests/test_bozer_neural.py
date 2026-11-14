import json
import tempfile
import unittest
from pathlib import Path
import torch
from bozer_neural.data import (
    get_balanced_batch, get_batch, get_supervised_batch,
    load_corpus, load_corpus_by_category, load_supervised_corpus,
    load_supervised_corpus_by_category, read_records, split_corpus,
    ensure_disjoint_prompts, format_prompt
)
from bozer_neural.evaluate import evaluate
from bozer_neural.config import BozerConfig
from bozer_neural.model import BozerTransformer
from bozer_neural.tokenizer import BOS, EOS, BozerByteTokenizer, BozerByteBPETokenizer
from bozer_neural.train import train, scheduled_learning_rate
from bozer_neural.learning import submit_feedback, approve_feedback
from bozer_neural.validate_corpus import validate_splits


CATEGORIES = [
    "dialogue_naturel", "comprehension_demande", "raisonnement_resolution",
    "planification_missions", "utilisation_outils_verification",
    "securite_confidentialite_honnetete",
]


class NeuralTests(unittest.TestCase):
    def test_byte_tokenizer_round_trip(self):
        text = "Bonjour BOZER — ça va ?"
        tokenizer = BozerByteTokenizer()
        self.assertEqual(tokenizer.decode(tokenizer.encode(text)), text)
        self.assertEqual(tokenizer.vocab_size, 259)

    def test_corpus_trained_bpe_round_trip_and_serialization(self):
        text = "BOZER comprend le français. BOZER répond naturellement.\n"
        tokenizer = BozerByteBPETokenizer.train(text * 8, vocab_size=320)
        self.assertGreater(tokenizer.vocab_size, 259)
        encoded = tokenizer.encode("BOZER répond en français — oui !")
        self.assertEqual(tokenizer.decode(encoded), "BOZER répond en français — oui !")
        restored = BozerByteBPETokenizer.from_dict(tokenizer.to_dict())
        self.assertEqual(restored.decode(encoded), "BOZER répond en français — oui !")
        self.assertEqual(restored.vocab_size, tokenizer.vocab_size)

    def test_parameter_count_and_forward(self):
        model = BozerTransformer()
        count = sum(parameter.numel() for parameter in model.parameters() if parameter.requires_grad)
        self.assertEqual(count, 124864)
        x = torch.randint(0, 259, (2, 32))
        logits, loss = model(x, x)
        self.assertEqual(tuple(logits.shape), (2, 32, 259))
        self.assertTrue(torch.isfinite(loss).item())
        loss.backward()
        self.assertIsNotNone(model.token_embedding.weight.grad)

    def test_batch_alignment_and_split(self):
        tokens = torch.arange(1000)
        train_part, val_part = split_corpus(tokens, 0.2, 32)
        self.assertEqual(int(train_part[-1]) + 1, int(val_part[0]))
        x, y = get_batch(train_part, 4, 32)
        self.assertTrue(torch.equal(x[:, 1:], y[:, :-1]))

    def test_jsonl_loader_training_checkpoint_and_evaluation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            train_file, val_file, test_file = root/"train.jsonl", root/"val.jsonl", root/"test.jsonl"
            output = root/"model.pt"
            train_rows = [
                {"category": "dialogue_naturel", "user": f"Bonjour, comment vas-tu numéro {i} ?",
                 "assistant": "Je vais bien, merci. Je peux t'aider."}
                for i in range(80)
            ]
            val_rows = [
                {"category": "dialogue_naturel", "user": f"Question de validation inédite numéro {i}",
                 "assistant": "Voici une réponse de validation suffisamment longue."}
                for i in range(20)
            ]
            test_rows = [
                {"category": "dialogue_naturel", "user": f"Question de test jamais vue numéro {i}",
                 "assistant": "Réponse de référence réservée au test."}
                for i in range(20)
            ]
            for path, rows in ((train_file, train_rows), (val_file, val_rows), (test_file, test_rows)):
                path.write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n",
                                 encoding="utf-8")

            tokens = load_corpus(train_file)
            self.assertGreater(tokens.numel(), 128)
            first, last, best = train(
                str(train_file), str(output), steps=2, batch_size=2,
                eval_interval=1, eval_batches=1, validation_data_path=str(val_file),
            )
            self.assertTrue(output.is_file())
            saved = torch.load(output, map_location="cpu", weights_only=False)
            self.assertEqual(saved["format"], "bozer-neural-v0.4")
            self.assertEqual(saved["training"]["split_mode"], "separate_file")
            self.assertEqual(saved["config"]["vocab_size"], saved["tokenizer"]["vocab_size"])
            self.assertIn("optimizer_state_dict", saved)
            self.assertEqual(saved["training"]["global_step"], 2)
            self.assertEqual(saved["training"]["training_objective"], "assistant_answer_tokens_and_eos_only")
            self.assertIn("dialogue_naturel", saved["training"]["category_validation_loss"])
            self.assertEqual(saved["training"]["split_audit"]["overlap"], 0)
            self.assertTrue(output.with_name("model.best.pt").is_file())
            self.assertTrue(all(torch.isfinite(torch.tensor(value)).item() for value in (first, last, best)))

            result = evaluate(str(output), str(test_file), batches=1, batch_size=2,
                              samples=1, max_new_tokens=8)
            self.assertEqual(len(result["examples"]), 1)
            self.assertEqual(result["training_objective"], "assistant_answer_tokens_and_eos_only")
            self.assertIn("dialogue_naturel", result["category_test_loss"])
            self.assertGreater(result["tokenizer_vocab_size"], 259)
            self.assertIn("generated", result["examples"][0])

    def test_runtime_split_guard_rejects_duplicate_prompts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            training = root / "train_fr.jsonl"
            validation = root / "validation_fr.jsonl"
            base = {"category": "dialogue_naturel", "user": "  Bonjour   BOZER ",
                    "assistant": "Bonjour !"}
            duplicate = {"category": "dialogue_naturel", "user": "bonjour bozer",
                         "assistant": "Salut !"}
            training.write_text(json.dumps(base, ensure_ascii=False) + chr(10), encoding="utf-8")
            validation.write_text(json.dumps(duplicate, ensure_ascii=False) + chr(10), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "leakage"):
                ensure_disjoint_prompts(training, validation)
            with self.assertRaisesRegex(ValueError, "different files"):
                ensure_disjoint_prompts(training, training)

    def test_runtime_split_guard_rejects_history_leakage(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            training = root / "train_fr.jsonl"
            validation = root / "validation_fr.jsonl"
            train_row = {
                "category": "dialogue_naturel", "user": "Question actuelle",
                "assistant": "Réponse actuelle",
                "history": [{"user": "Question déjà vue", "assistant": "Réponse passée"}],
            }
            validation_row = {
                "category": "dialogue_naturel", "user": "Question déjà vue",
                "assistant": "Une autre réponse",
            }
            training.write_text(json.dumps(train_row, ensure_ascii=False) + chr(10), encoding="utf-8")
            validation.write_text(json.dumps(validation_row, ensure_ascii=False) + chr(10), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "history turns"):
                ensure_disjoint_prompts(training, validation)

    def test_corpus_validation_rejects_history_leakage_between_splits(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = [Path(tmp) / name for name in ("train.jsonl", "validation.jsonl", "test.jsonl")]
            for index, path in enumerate(paths):
                rows = [
                    {"category": category, "user": f"question {index} {category}",
                     "assistant": "réponse"}
                    for category in CATEGORIES
                ]
                if index == 0:
                    rows[0]["history"] = [
                        {"user": "une ancienne demande", "assistant": "ancienne réponse"}
                    ]
                if index == 1:
                    rows[0]["user"] = "une ancienne demande"
                path.write_text(
                    "\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n",
                    encoding="utf-8",
                )
            with self.assertRaisesRegex(ValueError, "dialogue-history leakage"):
                validate_splits(*map(str, paths))

    def test_corpus_validation_rejects_leakage(self):
        with tempfile.TemporaryDirectory() as tmp:
            files = [Path(tmp)/"a.jsonl", Path(tmp)/"b.jsonl", Path(tmp)/"c.jsonl"]
            for index, path in enumerate(files):
                rows = [{"category": category, "user": f"question {index} {category}",
                         "assistant": "réponse"} for category in CATEGORIES]
                path.write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n",
                                 encoding="utf-8")
            report = validate_splits(*map(str, files))
            self.assertEqual(len(report), 3)
            files[1].write_text(files[0].read_text(encoding="utf-8"), encoding="utf-8")
            with self.assertRaises(ValueError):
                validate_splits(*map(str, files))

    def test_controlled_feedback_learning_and_resume(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pending = root / "pending.jsonl"
            training = root / "train.jsonl"
            validation = root / "validation.jsonl"
            checkpoint = root / "first.pt"
            next_checkpoint = root / "second.pt"
            rows = [
                {"category": "dialogue_naturel", "user": f"Bonjour numéro {i}",
                 "assistant": "Bonjour ! Comment puis-je t'aider aujourd'hui ?"}
                for i in range(80)
            ]
            val_rows = [
                {"category": "dialogue_naturel", "user": f"Validation inédite {i}",
                 "assistant": "Je peux t'aider avec une réponse claire et utile."}
                for i in range(20)
            ]
            training.write_text(
                "".join(json.dumps(x, ensure_ascii=False) + chr(10) for x in rows),
                encoding="utf-8")
            validation.write_text(
                "".join(json.dumps(x, ensure_ascii=False) + chr(10) for x in val_rows),
                encoding="utf-8")

            feedback_id = submit_feedback(
                pending, "dialogue_naturel", "Salut BOZER",
                "Salut ! Je suis là pour t'aider."
            )
            self.assertTrue(feedback_id)
            with self.assertRaises(ValueError):
                approve_feedback(pending, root / "test_fr.jsonl", feedback_id)
            report = approve_feedback(pending, training, feedback_id)
            self.assertEqual(report, {"approved": 1, "duplicates_skipped": 0, "pending": 0})
            self.assertEqual(len(training.read_text(encoding="utf-8").splitlines()), 81)
            evaluation_dir = root / "evaluation"
            evaluation_dir.mkdir()
            extra_id = submit_feedback(
                pending, "dialogue_naturel", "Bonjour pour le dossier secondaire",
                "Bonjour, je peux aussi examiner ce dossier."
            )
            extra_report = approve_feedback(
                pending, evaluation_dir / "train_extra.jsonl", extra_id
            )
            self.assertEqual(extra_report["approved"], 1)

            train(str(training), str(checkpoint), steps=2, batch_size=2,
                  eval_interval=1, eval_batches=1, validation_data_path=str(validation))
            first = torch.load(checkpoint, map_location="cpu", weights_only=False)
            self.assertEqual(first["format"], "bozer-neural-v0.4")
            train(str(training), str(next_checkpoint), steps=2, batch_size=2,
                  eval_interval=1, eval_batches=1, validation_data_path=str(validation),
                  resume_checkpoint=str(checkpoint))
            second = torch.load(next_checkpoint, map_location="cpu", weights_only=False)
            self.assertEqual(second["training"]["resumed_from"], str(checkpoint))
            self.assertEqual(second["training"]["global_step"], 4)
            self.assertEqual(second["tokenizer"], first["tokenizer"])
            self.assertIn("optimizer_state_dict", second)

    def test_read_records_rejects_malformed_history(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad_history.jsonl"
            row = {
                "category": "dialogue_naturel",
                "user": "Bonjour",
                "assistant": "Salut",
                "history": [{"user": "Question précédente", "assistant": ""}],
            }
            path.write_text(json.dumps(row, ensure_ascii=False) + chr(10), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "history turn 1"):
                read_records(path)

    def test_multiturn_history_is_included_in_prompt_context(self):
        item = {
            "category": "dialogue_naturel",
            "history": [
                {"user": "Je préfère les réponses courtes.", "assistant": "Compris."},
                {"user": "Explique aussi les calculs.", "assistant": "Je montrerai les étapes."},
            ],
            "user": "Combien font 12 plus 8 ?",
            "assistant": "12 + 8 = 20.",
        }
        prompt = format_prompt(item)
        self.assertIn("Je préfère les réponses courtes.", prompt)
        self.assertIn("Je montrerai les étapes.", prompt)
        self.assertTrue(prompt.endswith("Utilisateur : Combien font 12 plus 8 ?\nBOZER : "))

    def test_jsonl_loader_adds_boundaries_to_each_dialogue(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "dialogues.jsonl"
            rows = [
                {"category": "dialogue_naturel", "user": "Bonjour", "assistant": "Salut"},
                {"category": "dialogue_naturel", "user": "Tu es là ?", "assistant": "Oui"},
            ]
            path.write_text(
                "\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n",
                encoding="utf-8")
            records = read_records(path)
            tokens = load_corpus(path, BozerByteTokenizer()).tolist()
            category_tokens = load_corpus_by_category(path, BozerByteTokenizer())
            self.assertEqual(len(records), 2)
            self.assertEqual(tokens[0], BOS)
            self.assertEqual(tokens.count(BOS), 2)
            self.assertEqual(tokens.count(EOS), 2)
            self.assertEqual(set(category_tokens), {"dialogue_naturel"})
            self.assertGreater(category_tokens["dialogue_naturel"].numel(), 0)

    def test_batch_sampler_includes_last_legal_window(self):
        tokens = torch.arange(30)
        x, y = get_batch(
            tokens, batch_size=4096, context_length=8,
            generator=torch.Generator().manual_seed(7)
        )
        matching = (x[:, 0] == 21).nonzero(as_tuple=True)[0]
        self.assertGreater(matching.numel(), 0)
        self.assertEqual(int(y[matching[0], -1]), 29)

    def test_config_validation_and_learning_rate_schedule(self):
        with self.assertRaises(ValueError):
            BozerConfig(n_heads=0)
        with self.assertRaises(ValueError):
            BozerConfig(dropout=1.0)
        self.assertAlmostEqual(
            scheduled_learning_rate(0, 100, 1e-3, warmup_steps=10), 1e-4
        )
        self.assertAlmostEqual(
            scheduled_learning_rate(100, 100, 1e-3, warmup_steps=10),
            1e-4, places=8
        )

    def test_balanced_sampler_gives_each_category_one_row(self):
        sources = {
            f"category_{index}": torch.full((300,), index, dtype=torch.long)
            for index in range(6)
        }
        x, y = get_balanced_batch(
            sources, batch_size=6, context_length=16,
            generator=torch.Generator().manual_seed(11)
        )
        self.assertEqual(sorted(x[:, 0].tolist()), [0, 1, 2, 3, 4, 5])
        self.assertTrue(torch.equal(x[:, 1:], y[:, :-1]))

    def test_supervised_mask_only_trains_assistant_answer_and_eos(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "supervised.jsonl"
            row = {
                "category": "dialogue_naturel",
                "user": "Bonjour, tu es disponible ?",
                "assistant": "Oui, je suis disponible."
            }
            path.write_text(json.dumps(row, ensure_ascii=False) + chr(10), encoding="utf-8")
            tokenizer = BozerByteBPETokenizer.train(
                "Utilisateur : Bonjour, tu es disponible ?\nBOZER : Oui, je suis disponible.",
                vocab_size=320
            )
            tokens, mask = load_supervised_corpus(path, tokenizer)
            prompt_ids = tokenizer.encode(
                "Utilisateur : Bonjour, tu es disponible ?\nBOZER : ",
                add_special_tokens=False
            )
            answer_ids = tokenizer.encode(
                "Oui, je suis disponible.", add_special_tokens=False
            )
            self.assertEqual(int(mask.sum()), len(answer_ids) + 1)
            self.assertTrue(torch.equal(mask[:1 + len(prompt_ids)],
                                        torch.zeros(1 + len(prompt_ids))))
            self.assertTrue(torch.equal(mask[1 + len(prompt_ids):],
                                        torch.ones(len(answer_ids) + 1)))
            x, y, selected_mask = get_supervised_batch(
                tokens, mask, batch_size=4, context_length=32,
                generator=torch.Generator().manual_seed(5)
            )
            self.assertTrue(torch.all(selected_mask.sum(dim=1) > 0))
            model = BozerTransformer(BozerConfig(vocab_size=tokenizer.vocab_size))
            _, loss = model(x, y, loss_mask=selected_mask)
            self.assertTrue(torch.isfinite(loss).item())

    def test_masked_model_loss_rejects_empty_mask(self):
        model = BozerTransformer()
        ids = torch.randint(0, 259, (2, 8))
        with self.assertRaises(ValueError):
            model(ids, ids, loss_mask=torch.zeros_like(ids, dtype=torch.float32))

    def test_category_supervised_streams_preserve_masks(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "category.jsonl"
            rows = [
                {"category": "dialogue_naturel", "user": f"Bonjour {i}",
                 "assistant": "Je suis disponible pour t'aider avec cette demande."}
                for i in range(8)
            ]
            path.write_text(
                "\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n",
                encoding="utf-8")
            tokenizer = BozerByteBPETokenizer.train(
                "\n".join(f"Utilisateur : Bonjour {i}\nBOZER : Je suis disponible pour t'aider avec cette demande." for i in range(8)),
                vocab_size=320
            )
            grouped = load_supervised_corpus_by_category(path, tokenizer)
            self.assertEqual(set(grouped), {"dialogue_naturel"})
            values = grouped["dialogue_naturel"]
            self.assertEqual(values["tokens"].numel(), values["loss_mask"].numel())
            x, y, mask = get_supervised_batch(
                values["tokens"], values["loss_mask"], 4, 24,
                generator=torch.Generator().manual_seed(17)
            )
            self.assertTrue(torch.all(mask.sum(dim=1) > 0))


if __name__ == "__main__":
    unittest.main()
