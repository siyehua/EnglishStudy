import unittest
from unittest.mock import patch

from app.word_forms.enricher import (
    WordFormEnricher,
    fallback_variants,
    fixed_variants_for,
    parse_word_form_content,
)
from app.word_forms.resolver import WordFormResolver


class WordFormEnricherTest(unittest.TestCase):
    def test_parse_word_form_content_includes_headword_and_relation(self) -> None:
        result = parse_word_form_content(
            """
            {
              "headword": "protect",
              "currentPartOfSpeech": "verb",
              "currentPartOfSpeechLabel": "verb",
              "relation": {
                "type": "past_tense_of",
                "target": "protect",
                "label": "protect past"
              },
              "variantGroups": [
                {
                  "partOfSpeech": "verb",
                  "label": "verb",
                  "isCurrent": true,
                  "variants": [
                    {"type": "base", "label": "base", "value": "protect"},
                    {"type": "future", "label": "future", "value": "will protect"}
                  ]
                }
              ]
            }
            """
        )

        self.assertIsNotNone(result)
        assert result is not None
        self.assertEqual(result.headword, "protect")
        self.assertEqual(result.current_part_of_speech, "verb")
        self.assertEqual(result.relation.type if result.relation else None, "past_tense_of")
        self.assertEqual(result.variant_groups[0].variants[1].value, "will protect")

    def test_parse_word_form_content_includes_variant_groups(self) -> None:
        result = parse_word_form_content(
            """
            {
              "headword": "show",
              "currentPartOfSpeech": "noun",
              "currentPartOfSpeechLabel": "noun",
              "relation": {"type": "plural_of", "target": "show", "label": "show plural"},
              "variantGroups": [
                {
                  "partOfSpeech": "noun",
                  "label": "noun",
                  "isCurrent": true,
                  "variants": [
                    {"type": "singular", "label": "singular", "value": "show"},
                    {"type": "plural", "label": "plural", "value": "shows"}
                  ]
                },
                {
                  "partOfSpeech": "verb",
                  "label": "verb",
                  "isCurrent": false,
                  "variants": [
                    {"type": "base", "label": "base", "value": "show"},
                    {"type": "third_person_singular", "label": "third", "value": "shows"}
                  ]
                }
              ]
            }
            """
        )

        self.assertIsNotNone(result)
        assert result is not None
        self.assertEqual(result.current_part_of_speech, "noun")
        self.assertEqual(len(result.variant_groups), 2)
        self.assertTrue(result.variant_groups[0].isCurrent)
        self.assertEqual(result.variant_groups[0].variants[1].value, "shows")

    def test_enrich_uses_llm_headword_for_inflected_surface(self) -> None:
        word_form = WordFormResolver().resolve("protected", "The files were protected.")
        with patch(
            "app.word_forms.enricher.lookup_word_form",
            return_value=parse_word_form_content(
                """
                {
                  "headword":"protect",
                  "currentPartOfSpeech":"verb",
                  "currentPartOfSpeechLabel":"verb",
                  "relation":{"type":"past_tense_of","target":"protect","label":"protect past"},
                  "variantGroups":[{
                    "partOfSpeech":"verb",
                    "label":"verb",
                    "isCurrent":true,
                    "variants":[
                      {"type":"base","label":"base","value":"protect"},
                      {"type":"future","label":"future","value":"will protect"}
                    ]
                  }]
                }
                """
            ),
        ) as lookup:
            enriched = WordFormEnricher().enrich(word_form, "The files were protected.")

        lookup.assert_called_once()
        self.assertEqual(lookup.call_args.kwargs["surface"], "protected")
        self.assertEqual(enriched.headword, "protect")
        self.assertEqual(enriched.currentPartOfSpeech, "verb")
        self.assertEqual(enriched.relation.type if enriched.relation else None, "past_tense_of")
        self.assertEqual(enriched.variantGroups[0].variants[0].value, "protect")
        self.assertEqual(enriched.variantGroups[0].variants[1].value, "will protect")

    def test_fallback_does_not_invent_word_family_when_llm_unavailable(self) -> None:
        word_form = WordFormResolver().resolve("eggs", "She eats eggs.")
        variants = fallback_variants(word_form)

        self.assertEqual(len(variants), 1)
        self.assertEqual(variants[0].value, "eggs")

    def test_fixed_be_variants_are_complete(self) -> None:
        word_form = WordFormResolver().resolve("is", "This is good.")

        with patch("app.word_forms.enricher.lookup_word_form") as lookup:
            enriched = WordFormEnricher().enrich(word_form, "This is good.")

        lookup.assert_not_called()
        self.assertEqual(enriched.headword, "be")
        self.assertEqual(
            [item.value for item in enriched.variantGroups[0].variants],
            [
                "be",
                "am",
                "is",
                "are",
                "was",
                "were",
                "been",
                "being",
                "will be",
            ],
        )
        self.assertEqual(enriched.variantGroups[0].partOfSpeech, "verb")
        self.assertTrue(enriched.variantGroups[0].isCurrent)

    def test_fixed_variants_unknown_word_is_empty(self) -> None:
        self.assertEqual(fixed_variants_for("protect"), [])


if __name__ == "__main__":
    unittest.main()
