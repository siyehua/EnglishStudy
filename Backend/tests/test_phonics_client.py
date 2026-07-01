import unittest

from app.phonics.client import WordPhonicsClient, phones_to_ipa, split_phonemes_into_segments


class WordPhonicsClientTest(unittest.TestCase):
    def test_phones_to_ipa_strips_stress(self) -> None:
        self.assertEqual(phones_to_ipa(["SH", "OW1", "Z"]), "/\u0283o\u028az/")

    def test_split_phonemes_into_segments_by_vowel_nuclei(self) -> None:
        self.assertEqual(
            split_phonemes_into_segments(["W", "EH1", "B", "S", "AY1", "T"], 2),
            [["W", "EH1", "B"], ["S", "AY1", "T"]],
        )

    def test_lookup_shows(self) -> None:
        response = WordPhonicsClient(enable_llm=False).lookup("shows", ipa="/\u0283o\u028az/")

        self.assertTrue(response.found)
        self.assertEqual(response.ipa, "/\u0283o\u028az/")
        self.assertEqual([segment.text for segment in response.segments], ["shows"])
        self.assertEqual([segment.ipa for segment in response.segments], ["/\u0283o\u028az/"])

    def test_lookup_website_uses_two_segments(self) -> None:
        response = WordPhonicsClient(enable_llm=False).lookup("website")

        self.assertTrue(response.found)
        self.assertEqual([segment.text for segment in response.segments], ["web", "site"])
        self.assertEqual([segment.ipa for segment in response.segments], ["/w\u025bb/", "/sa\u026at/"])

    def test_lookup_westminster_uses_manual_segments(self) -> None:
        response = WordPhonicsClient(enable_llm=False).lookup("westminster")

        self.assertTrue(response.found)
        self.assertEqual([segment.text for segment in response.segments], ["west", "min", "ster"])
        self.assertEqual([segment.ipa for segment in response.segments], ["/w\u025bst/", "/m\u026an/", "/st\u025d/"])

    def test_lookup_limited_keeps_whole_word_without_llm(self) -> None:
        response = WordPhonicsClient(enable_llm=False).lookup("limited")

        self.assertTrue(response.found)
        self.assertEqual([segment.text for segment in response.segments], ["limited"])


if __name__ == "__main__":
    unittest.main()
