import json
import tempfile
import unittest
from pathlib import Path
import torch
from bozer_neural.data import get_batch, load_corpus, split_corpus
from bozer_neural.evaluate import evaluate
from bozer_neural.model import BozerTransformer
from bozer_neural.tokenizer import BozerByteTokenizer, BozerByteBPETokenizer
from bozer_neural.train import train
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
            self.assertEqual(saved["format"], "bozer-neural-v0.3")
            self.assertEqual(saved["training"]["split_mode"], "separate_file")
            self.assertEqual(saved["config"]["vocab_size"], saved["tokenizer"]["vocab_size"])
            self.assertTrue(all(torch.isfinite(torch.tensor(value)).item() for value in (first, last, best)))

            result = evaluate(str(output), str(test_file), batches=1, batch_size=2,
                              samples=1, max_new_tokens=8)
            self.assertEqual(len(result["examples"]), 1)
            self.assertGreater(result["tokenizer_vocab_size"], 259)
            self.assertIn("generated", result["examples"][0])

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
            report = approve_feedback(pending, training, feedback_id)
            self.assertEqual(report, {"approved": 1, "duplicates_skipped": 0, "pending": 0})
            self.assertEqual(len(training.read_text(encoding="utf-8").splitlines()), 81)
            with self.assertRaises(ValueError):
                approve_feedback(pending, root / "test.jsonl")

            train(str(training), str(checkpoint), steps=2, batch_size=2,
                  eval_interval=1, eval_batches=1, validation_data_path=str(validation))
            first = torch.load(checkpoint, map_location="cpu", weights_only=False)
            self.assertEqual(first["format"], "bozer-neural-v0.3")
            train(str(training), str(next_checkpoint), steps=2, batch_size=2,
                  eval_interval=1, eval_batches=1, validation_data_path=str(validation),
                  resume_checkpoint=str(checkpoint))
            second = torch.load(next_checkpoint, map_location="cpu", weights_only=False)
            self.assertEqual(second["training"]["resumed_from"], str(checkpoint))
            self.assertEqual(second["tokenizer"], first["tokenizer"])


if __name__ == "__main__":
    unittest.main()
