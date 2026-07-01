import unittest
from unittest.mock import patch

from app.phonics.client import WordPhonicsClient, phones_to_ipa, split_phonemes_into_segments
from app.schemas import WordPhonicsChunk, WordPhonicsSpan


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

    def test_lookup_uses_llm_fallback_when_cmudict_has_no_pronunciation(self) -> None:
        fallback_chunks = [
            WordPhonicsChunk(
                text="clas",
                ipa="/klæs/",
                phonemes=["K", "L", "AE1", "S"],
                spans=[WordPhonicsSpan(text="clas", silent=False)],
                stress="primary",
                rule="clas 按 /klæs/ 记忆。",
            ),
            WordPhonicsChunk(
                text="si",
                ipa="/ə/",
                phonemes=["AH0"],
                spans=[WordPhonicsSpan(text="si", silent=False)],
                stress="unstressed",
                rule="si 在非重读位置弱读。",
            ),
            WordPhonicsChunk(
                text="fier",
                ipa="/faɪər/",
                phonemes=["F", "AY2", "ER0"],
                spans=[WordPhonicsSpan(text="fier", silent=False)],
                stress="secondary",
                rule="fier 近似 /faɪər/。",
            ),
        ]

        with patch("app.phonics.client.lookup_llm_memory_chunks", return_value=fallback_chunks):
            response = WordPhonicsClient(enable_llm=True).lookup("classifier")

        self.assertTrue(response.found)
        self.assertEqual(response.verification, "llm_fallback")
        self.assertEqual(response.source, "deepseek")
        self.assertEqual(response.ipa, "/klæsəfaɪər/")
        self.assertEqual([segment.text for segment in response.segments], ["clas", "si", "fier"])


if __name__ == "__main__":
    unittest.main()
