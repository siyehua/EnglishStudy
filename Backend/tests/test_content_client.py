import unittest

from app.content.client import (
    ContentFeed,
    clean_text,
    clean_gutenberg_text,
    infer_publication_date,
    normalize_title,
    parse_dialogue_lines,
    parse_englishclub_reference_body,
    parse_rss_items,
)


class ContentClientTest(unittest.TestCase):
    def test_parse_rss_items(self) -> None:
        items = parse_rss_items(
            """
            <rss><channel><item>
                <title>Hello</title>
                <link>https://example.com/hello</link>
                <guid>item-1</guid>
                <pubDate>Fri, 26 Jun 2026 10:00:00 GMT</pubDate>
                <description><![CDATA[<p>Readable text.</p>]]></description>
            </item></channel></rss>
            """
        )

        self.assertEqual(len(items), 1)
        self.assertEqual(items[0]["title"], "Hello")
        self.assertEqual(items[0]["link"], "https://example.com/hello")
        self.assertEqual(items[0]["description"], "<p>Readable text.</p>")

    def test_clean_text_removes_markup(self) -> None:
        self.assertEqual(
            clean_text("<p>Hello&nbsp;world</p><script>bad()</script><p>Again</p>"),
            "Hello world\nAgain",
        )

    def test_parse_dialogue_lines(self) -> None:
        lines = parse_dialogue_lines("Alice\nHello Bob.\nBob\nHi Alice.")

        self.assertEqual(len(lines), 2)
        self.assertEqual(lines[0].speaker, "Alice")
        self.assertEqual(lines[0].text, "Hello Bob.")

    def test_normalize_title(self) -> None:
        self.assertEqual(
            normalize_title("BBC Learning English - 6 Minute English / A useful topic", "Fallback"),
            "A useful topic",
        )

    def test_parse_englishclub_reference_body_expands_short_forms(self) -> None:
        body = parse_englishclub_reference_body(
            "If you keep somebody on, you continue to employ them. "
            "Examples: keep sb on If I could, I'd keep everybody on. "
            "keep on sb We'll do our best to keep on everyone."
        )

        self.assertIn("For example:", body)
        self.assertIn("keep somebody on:", body)
        self.assertIn("keep on somebody:", body)
        self.assertNotIn(" sb ", body)

    def test_content_feed_defaults(self) -> None:
        feed = ContentFeed("Name", "https://example.com/rss", "NEWS", "B1")

        self.assertEqual(feed.limit, 10)
        self.assertEqual(feed.expanded_limit, 50)

    def test_infer_bbc_publication_date_from_episode_link(self) -> None:
        self.assertEqual(
            infer_publication_date(
                pub_date="",
                link="https://www.bbc.co.uk/learningenglish/english/features/6-minute-english_2026/ep-260625",
                feed=ContentFeed("BBC", "https://example.com/rss", "DIALOGUE", "B1"),
            ),
            "Thu, 25 Jun 2026 00:00:00 +0000",
        )

    def test_infer_publication_date_from_slash_date_link(self) -> None:
        self.assertEqual(
            infer_publication_date(
                pub_date="",
                link="https://www.economist.com/finance-and-economics/2026/06/25/will-ai-lower-interest-rates",
                feed=ContentFeed("Economist", "https://example.com/rss", "NEWS", "C1"),
            ),
            "Thu, 25 Jun 2026 00:00:00 +0000",
        )

    def test_clean_gutenberg_text_removes_boilerplate(self) -> None:
        body = clean_gutenberg_text(
            "Project metadata\n"
            "*** START OF THE PROJECT GUTENBERG EBOOK SAMPLE ***\n"
            "Title: Sample\n"
            "Author: Someone\n"
            "CHAPTER I\n"
            "This is the first readable paragraph.\n"
            "\n"
            "This is another paragraph.\n"
            "*** END OF THE PROJECT GUTENBERG EBOOK SAMPLE ***"
        )

        self.assertNotIn("START OF", body)
        self.assertNotIn("Title:", body)
        self.assertIn("This is the first readable paragraph.", body)


if __name__ == "__main__":
    unittest.main()
