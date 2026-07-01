import unittest
from unittest.mock import patch

from app.dictionary.client import enrich_with_phonetic_fallback, not_found, parse_dictionary_response


class DictionaryClientTest(unittest.TestCase):
    def test_parse_phonetic_and_audio(self) -> None:
        result = parse_dictionary_response(
            word="website",
            normalized="website",
            payload=[
                {
                    "word": "website",
                    "phonetics": [
                        {"text": "/\u02c8w\u025bbsa\u026at/", "audio": ""},
                        {
                            "text": "/\u02c8websa\u026at/",
                            "audio": "https://api.dictionaryapi.dev/media/pronunciations/en/website-us.mp3",
                        },
                    ],
                }
            ],
        )

        self.assertTrue(result.found)
        self.assertEqual(result.phonetic, "/\u02c8w\u025bbsa\u026at/")
        self.assertEqual(
            result.audioUrl,
            "https://api.dictionaryapi.dev/media/pronunciations/en/website-us.mp3",
        )

    def test_parse_missing_data(self) -> None:
        result = parse_dictionary_response(
            word="unknown",
            normalized="unknown",
            payload=[{"word": "unknown", "phonetics": []}],
        )

        self.assertFalse(result.found)
        self.assertIsNone(result.phonetic)
        self.assertIsNone(result.audioUrl)

    def test_fallback_can_fill_missing_phonetic(self) -> None:
        with patch(
            "app.dictionary.client.lookup_deepseek_phonetic",
            return_value=("/əˈmɛrɪkə/", "deepseek:test"),
        ):
            result = enrich_with_phonetic_fallback(not_found("America", "america"))

        self.assertTrue(result.found)
        self.assertEqual(result.phonetic, "/əˈmɛrɪkə/")
        self.assertEqual(result.source, "dictionaryapi.dev+deepseek:test")

    def test_fallback_keeps_existing_phonetic(self) -> None:
        result = parse_dictionary_response(
            word="website",
            normalized="website",
            payload=[{"word": "website", "phonetic": "/ˈwebsaɪt/"}],
        )

        with patch("app.dictionary.client.lookup_deepseek_phonetic") as fallback:
            enriched = enrich_with_phonetic_fallback(result)

        fallback.assert_not_called()
        self.assertEqual(enriched.phonetic, "/ˈwebsaɪt/")


if __name__ == "__main__":
    unittest.main()
