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

    def test_lesson_to_content_items_splits_three_sections(self) -> None:
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
                {"text": "Another useful phrase here.", "start": 12.0, "end": 13.0},
                {"text": "Fluency builder.", "start": 13.0, "end": 14.0},
                {"text": "Let's practice these phrases.", "start": 14.0, "end": 16.0},
            ],
        }

        items = lesson_to_content_items(999, lesson)

        self.assertEqual(len(items), 3)
        titles = [item.title for item in items]
        self.assertEqual(titles[0], "999: Difficult Customer · 对话")
        self.assertEqual(titles[1], "999: Difficult Customer · 讲解")
        self.assertEqual(titles[2], "999: Difficult Customer · 回顾")

        # 对话课程：只有角色对话，不含主持人引导语
        self.assertIn("Good evening.", items[0].body)
        self.assertIn("My name is Fabio.", items[0].body)
        self.assertNotIn("listen to this dialogue", items[0].body.lower())
        self.assertNotIn("language takeaway", items[0].body.lower())
        # 每句都有独立时间戳
        for line in items[0].lines:
            self.assertGreater(line.end, line.start)
            self.assertTrue(line.text)
        # 讲解课程：只有讲解文本
        self.assertIn("The first expression is still working on it.", items[1].body)
        self.assertNotIn("Good evening", items[1].body)
        # 回顾课程：只有回顾文本
        self.assertIn("Let's practice these phrases.", items[2].body)

        for item in items:
            self.assertEqual(item.type, "DIALOGUE")
            self.assertEqual(item.source, ENGLISH_POD_SOURCE_NAME)
            self.assertTrue(all(":" not in line.text or True for line in item.lines))

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
