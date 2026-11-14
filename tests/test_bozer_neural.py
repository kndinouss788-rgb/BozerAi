import tempfile
import unittest
from pathlib import Path
import torch
from bozer_neural.data import get_batch, load_corpus, split_corpus
from bozer_neural.model import BozerTransformer
from bozer_neural.tokenizer import BozerByteTokenizer
from bozer_neural.train import train
from bozer_neural.validate_corpus import validate_splits


class NeuralTests(unittest.TestCase):
    def test_tokenizer_round_trip(self):
        text = "Bonjour BOZER — ça va ?"
        tok = BozerByteTokenizer()
        self.assertEqual(tok.decode(tok.encode(text)), text)
        self.assertEqual(tok.vocab_size, 259)

    def test_parameter_count_and_forward(self):
        model = BozerTransformer()
        count = sum(p.numel() for p in model.parameters() if p.requires_grad)
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

    def test_jsonl_loader_and_training(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            train_file, val_file, output = root/"train.jsonl", root/"val.jsonl", root/"model.pt"
            rows = trainRowsLocal = [
                {"category":"dialogue_naturel","user":"Bonjour, comment vas-tu ?","assistant":"Je vais bien, merci."},
                {"category":"raisonnement_resolution","user":"Combien font 8 fois 9 ?","assistant":"8 × 9 = 72."}
            ]
            train_file.write_text("\\n".join(__import__("json").dumps(x, ensure_ascii=False) for x in rows)+"\\n", encoding="utf-8")
            val_file.write_text(("Utilisateur : bonjour\\nBOZER : oui\\n")*100, encoding="utf-8")
            tokens = load_corpus(train_file)
            self.assertGreater(tokens.numel(), 128)
            first, last, best = train(str(train_file), str(output), steps=2, batch_size=2,
                                      eval_interval=1, eval_batches=1,
                                      validation_data_path=str(val_file))
            self.assertTrue(output.is_file())
            saved = torch.load(output, map_location="cpu", weights_only=False)
            self.assertEqual(saved["training"]["split_mode"], "separate_file")
            self.assertTrue(all(torch.isfinite(torch.tensor(x)).item() for x in (first,last,best)))

    def test_corpus_validation_rejects_leakage(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cats = ["dialogue_naturel","comprehension_demande","raisonnement_resolution",
                    "planification_missions","utilisation_outils_verification",
                    "securite_confidentialite_honnetete"]
            files = [root/"a.jsonl",root/"b.jsonl",root/"c.jsonl"]
            for index, path in enumerate(files):
                rows = [{"category": cat, "user": f"question {index} {cat}",
                         "assistant": "réponse"} for cat in cats]
                path.write_text("\\n".join(__import__("json").dumps(x) for x in rows)+"\\n",encoding="utf-8")
            report = validate_splits(*map(str,files))
            self.assertEqual(len(report),3)
            files[1].write_text(files[0].read_text(encoding="utf-8"),encoding="utf-8")
            with self.assertRaises(ValueError):
                validate_splits(*map(str,files))


if __name__ == "__main__":
    unittest.main()
