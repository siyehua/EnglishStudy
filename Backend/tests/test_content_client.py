import unittest

from app.content.client import (
    ENGLISH_POD_SOURCE_NAME,
    build_content_filters,
    englishpod_level,
    format_lesson_title,
    lesson_to_content_items,
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

    def test_format_lesson_title(self) -> None:
        self.assertEqual(format_lesson_title(1, "1 Elementary - Difficult Customer"), "1: Difficult Customer")
        self.assertEqual(format_lesson_title(10, "10 The Office - Driving Sales"), "10: Driving Sales")
        self.assertEqual(format_lesson_title(18, "18 Upper-Intermediate - Protest!"), "18: Protest!")

    def test_lesson_to_content_items_returns_single_article(self) -> None:
        lesson = {
            "title": "1 Elementary - Difficult Customer",
            "audio": "./assets/englishpod_B0001pb.mp3",
            "content": [
                {"text": "Welcome to EnglishPod.", "start": 0.0, "end": 2.0},
                {"text": "Let's listen to this dialogue.", "start": 2.0, "end": 4.0},
                {"text": "Good evening. My name is Fabio.", "start": 4.0, "end": 6.0},
                {"text": "May I take your order?", "start": 6.0, "end": 8.0},
                {"text": "Language takeaway.", "start": 8.0, "end": 10.0},
                {"text": "The first expression is still working on it.", "start": 10.0, "end": 12.0},
                {"text": "Fluency builder.", "start": 12.0, "end": 14.0},
                {"text": "Let's practice these phrases.", "start": 14.0, "end": 16.0},
            ],
        }

        items = lesson_to_content_items(999, lesson)

        # A lesson is a single article now (no dialogue/explanation/review split).
        self.assertEqual(len(items), 1)
        item = items[0]
        self.assertEqual(item.title, "999: Difficult Customer")
        self.assertEqual(item.type, "DIALOGUE")
        self.assertEqual(item.source, ENGLISH_POD_SOURCE_NAME)

        # The whole transcript is kept in one article.
        self.assertIn("Good evening.", item.body)
        self.assertIn("The first expression is still working on it.", item.body)
        self.assertIn("Let's practice these phrases.", item.body)

        # Every line has its own timestamp and a segment URL.
        for line in item.lines:
            self.assertGreater(line.end, line.start)
            self.assertTrue(line.text)
            self.assertIn("/ting/segment", line.audioUrl or "")

    def test_lesson_to_content_items_filters_by_level(self) -> None:
        lesson = {
            "title": "1 Elementary - X",
            "audio": "",
            "content": [{"text": "Hello.", "start": 0.0, "end": 1.0}],
        }

        self.assertEqual(lesson_to_content_items(999, lesson, requested_levels={"B1"}), [])
        self.assertTrue(lesson_to_content_items(999, lesson, requested_levels={"A2"}))

    def test_normalize_filter_values(self) -> None:
        self.assertEqual(normalize_filter_values(["dialogue", " NEWS ", "", "news"]), {"DIALOGUE", "NEWS"})

    def test_normalize_source_values(self) -> None:
        self.assertEqual(normalize_source_values(["EnglishPod", "", " "]), {"EnglishPod"})

    def test_stable_id(self) -> None:
        self.assertEqual(len(stable_id("englishpod-1-dialogue")), 64)


if __name__ == "__main__":
    unittest.main()
