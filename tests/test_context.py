import os
import tempfile
import unittest

from bozer_brain.context import build_context
from bozer_brain.memory import ConversationMemory
from bozer_brain.dialogue import DialogueEngine


class ContextTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.memory = ConversationMemory(os.path.join(self.temp_dir.name, "context.sqlite3"))

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_context_includes_recent_history_and_current_message(self):
        self.memory.add_turn("user", "Je préfère le français.")
        self.memory.add_turn("assistant", "Je te répondrai en français.")
        context = build_context(self.memory, "Et demain ?")
        self.assertIn("Je préfère le français.", context.prompt)
        self.assertIn("MESSAGE ACTUEL:\nEt demain ?", context.prompt)
        self.assertEqual(len(context.history), 2)

    def test_history_limit_and_character_limit(self):
        self.memory.add_turn("user", "A" * 40)
        self.memory.add_turn("assistant", "B" * 40)
        context = build_context(
            self.memory, "Question", history_limit=1, message_char_limit=10
        )
        self.assertEqual(len(context.history), 1)
        self.assertEqual(len(context.history[0][1]), 10)

    def test_context_rejects_empty_message(self):
        with self.assertRaises(ValueError):
            build_context(self.memory, " ")

    def test_dialogue_passes_context_to_responder(self):
        captured = []
        engine = DialogueEngine(
            self.memory, responder=lambda prompt: captured.append(prompt) or "Réponse"
        )
        engine.respond("Explique-moi cela")
        self.assertTrue(captured)
        self.assertIn("MESSAGE ACTUEL:", captured[0])
        self.assertIn("Explique-moi cela", captured[0])


if __name__ == "__main__":
    unittest.main()
