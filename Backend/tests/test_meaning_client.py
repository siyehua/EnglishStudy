import json
import unittest

from app.meaning.client import parse_meaning_content


class MeaningClientTest(unittest.TestCase):
    def test_parse_json_meanings(self) -> None:
        result = parse_meaning_content(
            json.dumps(
                {
                    "meanings": [
                        {
                            "partOfSpeech": "noun",
                            "label": "名词",
                            "meaning": "跑步；一段连续的事情",
                            "example": "Running helps me relax.",
                            "exampleChinese": "跑步帮助我放松。",
                        },
                        {
                            "partOfSpeech": "verb",
                            "label": "动词",
                            "meaning": "跑；经营；运行",
                            "example": "They are running a cafe.",
                            "exampleChinese": "他们正在经营一家咖啡馆。",
                        },
                    ],
                    "sentenceChinese": "她经营一家小商店。",
                },
                ensure_ascii=False,
            )
        )

        self.assertEqual(len(result.entries), 2)
        self.assertEqual(result.entries[0].partOfSpeech, "noun")
        self.assertEqual(result.entries[0].label, "名词")
        self.assertEqual(result.entries[0].meaning, "跑步；一段连续的事情")
        self.assertEqual(result.entries[0].example, "Running helps me relax.")
        self.assertEqual(result.entries[0].exampleChinese, "跑步帮助我放松。")
        self.assertEqual(result.entries[1].partOfSpeech, "verb")
        self.assertEqual(result.sentence_chinese, "她经营一家小商店。")

    def test_parse_dictionary_payload_and_filter_empty_values(self) -> None:
        result = parse_meaning_content(
            json.dumps(
                {
                    "meanings": {
                        "noun": "苹果",
                        "verb": "",
                        "adj": "苹果的；与苹果有关的",
                    }
                },
                ensure_ascii=False,
            )
        )

        self.assertEqual(len(result.entries), 2)
        self.assertEqual(result.entries[0].partOfSpeech, "noun")
        self.assertEqual(result.entries[1].partOfSpeech, "adjective")

    def test_parse_invalid_content_as_empty(self) -> None:
        result = parse_meaning_content("not json")
        self.assertEqual(result.entries, [])
        self.assertEqual(result.sentence_chinese, "")


if __name__ == "__main__":
    unittest.main()
