import tempfile
import unittest
from pathlib import Path
import torch
from bozer_neural.data import get_batch, split_corpus
from bozer_neural.model import BozerTransformer
from bozer_neural.tokenizer import BozerByteTokenizer
from bozer_neural.train import train


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

    def test_training_saves_validation_metrics(self):
        with tempfile.TemporaryDirectory() as tmp:
            corpus, output = Path(tmp) / "seed.txt", Path(tmp) / "model.pt"
            corpus.write_text(
                ("BOZER aide avec honnêteté. Utilisateur : Bonjour. "
                 "BOZER : Oui, je suis là.\n") * 80, encoding="utf-8"
            )
            first, last, best_val = train(
                str(corpus), str(output), steps=2, batch_size=2,
                eval_interval=1, eval_batches=1,
            )
            self.assertTrue(output.is_file())
            saved = torch.load(output, map_location="cpu", weights_only=False)
            self.assertEqual(saved["format"], "bozer-neural-v0.1")
            self.assertEqual(len(saved["training"]["eval_history"]), 2)
            self.assertTrue(all(torch.isfinite(torch.tensor(v)).item()
                                for v in (first, last, best_val)))


if __name__ == "__main__":
    unittest.main()
