import unittest
import torch

from bozer_neural.config import BozerConfig
from bozer_neural.data import get_batch
from bozer_neural.model import BozerTransformer
from bozer_neural.tokenizer import BozerByteTokenizer


class TokenizerTests(unittest.TestCase):
    def test_utf8_round_trip(self):
        tokenizer = BozerByteTokenizer()
        text = "Bonjour BOZER — ça va ?"
        self.assertEqual(tokenizer.decode(tokenizer.encode(text)), text)

    def test_byte_vocab(self):
        self.assertEqual(BozerByteTokenizer.vocab_size, 259)


class ModelTests(unittest.TestCase):
    def test_default_parameter_count(self):
        model = BozerTransformer()
        count = sum(p.numel() for p in model.parameters() if p.requires_grad)
        self.assertEqual(count, 124864)
        self.assertEqual(count, model.config.parameter_count())

    def test_forward_loss_and_gradients(self):
        model = BozerTransformer()
        x = torch.randint(0, 259, (2, 32))
        logits, loss = model(x, x)
        self.assertEqual(tuple(logits.shape), (2, 32, 259))
        self.assertTrue(torch.isfinite(loss).item())
        loss.backward()
        self.assertIsNotNone(model.token_embedding.weight.grad)

    def test_context_limit(self):
        model = BozerTransformer()
        with self.assertRaises(ValueError):
            model(torch.zeros((1, 129), dtype=torch.long))

    def test_batch_shapes(self):
        tokens = torch.arange(400)
        x, y = get_batch(tokens, 4, 32)
        self.assertEqual(tuple(x.shape), (4, 32))
        self.assertTrue(torch.equal(x[:, 1:], y[:, :-1]))


if __name__ == "__main__":
    unittest.main()
