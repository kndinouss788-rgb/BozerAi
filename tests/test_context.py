import os
import tempfile
import unittest

from bozer_brain.context import build_context
from bozer_brain.memory import ConversationMemory


class ContextTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.memory = ConversationMemory(os.path.join(self.temp_dir.name, "memory.sqlite3"))

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_context_contains_history_and_current_message(self):
        self.memory.add_turn("user", "Je préfère le français.")
        self.memory.add_turn("assistant", "Bien sûr.")
        context = build_context(self.memory, "Et demain ?")
        self.assertIn("Je préfère le français.", context.prompt)
        self.assertIn("MESSAGE ACTUEL:\nEt demain ?", context.prompt)

    def test_context_bounds_history_and_message_size(self):
        self.memory.add_turn("user", "x" * 100)
        context = build_context(self.memory, "Question", history_limit=1, message_char_limit=12)
        self.assertEqual(len(context.history[0][1]), 12)

    def test_zero_history_is_supported(self):
        self.memory.add_turn("user", "ancien échange")
        context = build_context(self.memory, "actuel", history_limit=0)
        self.assertEqual(context.history, [])
        self.assertIn("(aucun historique)", context.prompt)

    def test_invalid_limits_and_empty_message_rejected(self):
        with self.assertRaises(ValueError):
            build_context(self.memory, " ", history_limit=1)
        with self.assertRaises(ValueError):
            build_context(self.memory, "test", history_limit=-1)


if __name__ == "__main__":
    unittest.main()
