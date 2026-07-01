import unittest

from app.word_forms.resolver import WordFormResolver, normalize_surface


class WordFormResolverTest(unittest.TestCase):
    def setUp(self) -> None:
        self.resolver = WordFormResolver()

    def test_regular_plural_is_not_guessed_locally(self) -> None:
        result = self.resolver.resolve("eggs", "She eats eggs.")

        self.assertEqual(result.normalized, "eggs")
        self.assertEqual(result.headword, "eggs")
        self.assertEqual(result.pronunciationTarget, "eggs")
        self.assertIsNone(result.relation)
        self.assertEqual(result.source, "identity")

    def test_regular_past_is_not_guessed_locally(self) -> None:
        result = self.resolver.resolve("protected", "The files were protected.")

        self.assertEqual(result.headword, "protected")
        self.assertEqual(result.pronunciationTarget, "protected")
        self.assertIsNone(result.relation)
        self.assertEqual(result.source, "identity")

    def test_ing_form_is_not_guessed_locally(self) -> None:
        result = self.resolver.resolve("advertising", "Advertising can be creative.")

        self.assertEqual(result.headword, "advertising")
        self.assertEqual(result.pronunciationTarget, "advertising")
        self.assertIsNone(result.relation)
        self.assertEqual(result.source, "identity")

    def test_s_ending_singular_words_are_not_pluralized(self) -> None:
        for word in ("famous", "serious", "analysis", "basis"):
            with self.subTest(word=word):
                result = self.resolver.resolve(word, "")

                self.assertEqual(result.headword, word)
                self.assertIsNone(result.relation)

    def test_fixed_be_forms_resolve_to_be(self) -> None:
        result = self.resolver.resolve("is", "This is good.")

        self.assertEqual(result.headword, "be")
        self.assertEqual(result.pronunciationTarget, "is")
        self.assertEqual(result.relation.type if result.relation else None, "third_person_singular_of")
        self.assertEqual(result.relation.target if result.relation else None, "be")
        self.assertEqual(result.source, "fixed_form_table")

    def test_fixed_do_have_forms_resolve_locally(self) -> None:
        cases = {
            "does": ("do", "third_person_singular_of"),
            "did": ("do", "past_tense_of"),
            "has": ("have", "third_person_singular_of"),
            "had": ("have", "past_tense_of"),
        }
        for surface, (headword, relation_type) in cases.items():
            with self.subTest(surface=surface):
                result = self.resolver.resolve(surface, "")

                self.assertEqual(result.headword, headword)
                self.assertEqual(result.relation.type if result.relation else None, relation_type)
                self.assertEqual(result.source, "fixed_form_table")

    def test_contraction_has_expansion_but_keeps_contraction_headword(self) -> None:
        result = self.resolver.resolve("don't", "I don't know.")

        self.assertEqual(result.headword, "don't")
        self.assertEqual(result.pronunciationTarget, "don't")
        self.assertEqual(result.expansion, "do not")
        self.assertEqual(result.relation.type if result.relation else None, "contraction_of")
        self.assertEqual(result.relation.target if result.relation else None, "do not")

    def test_curly_apostrophe_contraction_normalizes(self) -> None:
        result = self.resolver.resolve("Don\u2019t", "Don\u2019t stop.")

        self.assertEqual(result.normalized, "don't")
        self.assertEqual(result.expansion, "do not")

    def test_identity_word(self) -> None:
        result = self.resolver.resolve("website", "Open the website.")

        self.assertEqual(result.headword, "website")
        self.assertEqual(result.pronunciationTarget, "website")
        self.assertIsNone(result.relation)
        self.assertIsNone(result.expansion)

    def test_normalize_surface_strips_punctuation(self) -> None:
        self.assertEqual(normalize_surface('"Eggs,"'), "eggs")


if __name__ == "__main__":
    unittest.main()
