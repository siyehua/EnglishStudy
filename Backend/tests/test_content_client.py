import unittest

from app.content.client import (
    ENGLISH_POD_SOURCE_NAME,
    build_content_filters,
    englishpod_audio_url,
    englishpod_level,
    lesson_to_content,
    normalize_filter_values,
    normalize_source_values,
    stable_id,
)


class ContentClientTest(unittest.TestCase):
    def test_englishpod_level_from_audio_letter(self) -> None:
        self.assertEqual(englishpod_level("1 Elementary - X", "./assets/englishpod_B0001pb.mp3"), "A2")
        self.assertEqual(englishpod_level("2 Intermediate - X", "./assets/englishpod_C0002pb.mp3"), "B1")
        self.assertEqual(englishpod_level("3 Upper Intermediate - X", "./assets/englishpod_D0003pb.mp3"), "B2")
        self.assertEqual(englishpod_level("4 Advanced - X", "./assets/englishpod_E0004pb.mp3"), "C1")

    def test_englishpod_level_from_title(self) -> None:
        self.assertEqual(englishpod_level("1 Elementary - Difficult Customer", ""), "A2")
        self.assertEqual(englishpod_level("37 Intermediate - X", ""), "B1")
        self.assertEqual(englishpod_level("18 Upper Intermediate - X", ""), "B2")
        self.assertEqual(englishpod_level("29 Advanced - X", ""), "C1")

    def test_englishpod_level_defaults_to_b1(self) -> None:
        self.assertEqual(englishpod_level("10 The Office - Driving Sales", ""), "B1")
        self.assertEqual(englishpod_level("365 Daily Life - Household Chores", ""), "B1")

    def test_lesson_to_content_maps_fields(self) -> None:
        lesson = {
            "title": "1 Elementary - Difficult Customer",
            "audio": "./assets/englishpod_B0001pb.mp3",
            "content": [
                {"text": "Good evening.", "trans": "晚上好。", "start": 0.0, "end": 2.0},
                {"text": "May I take your order?", "trans": "请问点餐吗？", "start": 2.0, "end": 4.0},
            ],
        }

        item = lesson_to_content(1, lesson)

        self.assertIsNotNone(item)
        assert item is not None
        self.assertEqual(item.title, "1 Elementary - Difficult Customer")
        self.assertEqual(item.type, "DIALOGUE")
        self.assertEqual(item.level, "A2")
        self.assertEqual(item.source, ENGLISH_POD_SOURCE_NAME)
        self.assertEqual(item.id, stable_id("englishpod-1"))
        self.assertEqual(item.body, "Good evening.\nMay I take your order?")
        self.assertEqual(item.audioUrl, "https://raw.githubusercontent.com/bitter999/EnglishPod/main/assets/englishpod_B0001pb.mp3")
        self.assertEqual(len(item.lines), 2)
        self.assertEqual(item.lines[0].speaker, "Narrator")
        self.assertEqual(item.lines[0].text, "Good evening.")
        self.assertEqual(item.lines[0].trans, "晚上好。")

    def test_lesson_to_content_skips_empty_text(self) -> None:
        lesson = {
            "title": "1 Elementary - X",
            "audio": "",
            "content": [
                {"text": "", "trans": "", "start": 0.0, "end": 1.0},
                {"text": "  ", "trans": "", "start": 1.0, "end": 2.0},
                {"text": "Hello.", "trans": "你好。", "start": 2.0, "end": 3.0},
            ],
        }

        item = lesson_to_content(1, lesson)

        self.assertIsNotNone(item)
        assert item is not None
        self.assertEqual(item.body, "Hello.")
        self.assertEqual(len(item.lines), 1)

    def test_lesson_to_content_returns_none_for_empty_body(self) -> None:
        lesson = {
            "title": "1 Elementary - X",
            "audio": "",
            "content": [{"text": " ", "trans": "", "start": 0.0, "end": 1.0}],
        }

        self.assertIsNone(lesson_to_content(1, lesson))

    def test_lesson_to_content_filters_by_level(self) -> None:
        lesson = {
            "title": "1 Elementary - X",
            "audio": "",
            "content": [{"text": "Hello.", "trans": "你好。", "start": 0.0, "end": 1.0}],
        }

        self.assertIsNone(lesson_to_content(1, lesson, requested_levels={"B1"}))
        self.assertIsNotNone(lesson_to_content(1, lesson, requested_levels={"A2"}))

    def test_englishpod_audio_url(self) -> None:
        self.assertEqual(
            englishpod_audio_url("./assets/englishpod_B0001pb.mp3"),
            "https://raw.githubusercontent.com/bitter999/EnglishPod/main/assets/englishpod_B0001pb.mp3",
        )
        self.assertIsNone(englishpod_audio_url(""))
        self.assertIsNone(englishpod_audio_url("   "))

    def test_normalize_filter_values(self) -> None:
        self.assertEqual(normalize_filter_values(["dialogue", " NEWS ", "", "news"]), {"DIALOGUE", "NEWS"})

    def test_normalize_source_values(self) -> None:
        self.assertEqual(normalize_source_values(["EnglishPod", "", " "]), {"EnglishPod"})

    def test_build_content_filters_counts_sources(self) -> None:
        item = lesson_to_content(
            1,
            {
                "title": "1 Elementary - X",
                "audio": "",
                "content": [{"text": "Hello.", "trans": "你好。", "start": 0.0, "end": 1.0}],
            },
        )
        assert item is not None

        filters = build_content_filters([item])

        self.assertEqual([source.id for source in filters.sources], [ENGLISH_POD_SOURCE_NAME])
        self.assertEqual(filters.sources[0].count, 1)
        self.assertEqual([type_.id for type_ in filters.types], ["DIALOGUE"])
        self.assertEqual([level.id for level in filters.levels], ["A2"])


if __name__ == "__main__":
    unittest.main()
