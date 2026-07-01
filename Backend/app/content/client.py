from __future__ import annotations

import concurrent.futures
import hashlib
import html
import re
import urllib.request
import xml.etree.ElementTree as ET
from collections.abc import Iterable
from dataclasses import dataclass
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from html.parser import HTMLParser
from time import monotonic
from urllib.parse import urljoin

from app.schemas import (
    ContentFilterOptionResponse,
    ContentFiltersResponse,
    ContentItemResponse,
    DialogueLineResponse,
)


class ContentFetchError(Exception):
    pass


class ContentClient:
    def fetch(
        self,
        fetch_more: bool,
        types: list[str],
        levels: list[str],
        sources: list[str] | None = None,
    ) -> tuple[list[ContentItemResponse], ContentFiltersResponse]:
        requested_types = normalize_filter_values(types)
        requested_levels = normalize_filter_values(levels)
        requested_sources = normalize_source_values(sources or [])
        feeds = [
            feed
            for feed in CONTENT_FEEDS
            if not requested_types or feed.type in requested_types
        ]
        if requested_sources:
            feeds = [feed for feed in feeds if feed.name in requested_sources]

        deadline = monotonic() + CONTENT_FETCH_DEADLINE_SECONDS
        results: list[list[ContentItemResponse]] = []
        executor = concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS)
        try:
            future_to_feed = {
                executor.submit(fetch_feed_safely, feed, fetch_more, requested_levels, deadline): feed
                for feed in feeds
            }
            done, pending = concurrent.futures.wait(
                future_to_feed,
                timeout=max(0.1, deadline - monotonic()),
            )
            for future in done:
                results.append(future.result())
            for future in pending:
                future.cancel()
        finally:
            executor.shutdown(wait=False, cancel_futures=True)

        by_id: dict[str, ContentItemResponse] = {}
        for item in [content for feed_items in results for content in feed_items]:
            by_id[item.id] = item

        items = sorted(
            by_id.values(),
            key=lambda item: parsed_date_timestamp(item.date),
            reverse=True,
        )
        return items, build_content_filters(items)


def fetch_feed_safely(
    feed: "ContentFeed",
    fetch_more: bool,
    requested_levels: set[str],
    deadline: float,
) -> list[ContentItemResponse]:
    try:
        return fetch_feed(feed, fetch_more, requested_levels, deadline)
    except Exception:
        return []


def fetch_feed(
    feed: "ContentFeed",
    fetch_more: bool,
    requested_levels: set[str],
    deadline: float,
) -> list[ContentItemResponse]:
    if is_deadline_expired(deadline):
        return []

    xml = download_text(feed.url, timeout=request_timeout(deadline))
    items = parse_rss_items(xml)
    limit = feed.expanded_limit if fetch_more else feed.limit
    content_items: list[ContentItemResponse] = []

    for rss_item in items:
        if is_deadline_expired(deadline):
            break

        title = normalize_title(clean_text(rss_item.get("title", "")), feed.name)
        if not title:
            continue
        if is_feed_title_noise(title, feed):
            continue

        link = rss_item.get("link", "")
        rss_content = clean_text(rss_item.get("content", "") or rss_item.get("description", ""))
        full_text = fetch_article_body(link, feed, deadline) or rss_content
        if feed.body_parser == "ESLPOD_DAILY":
            full_text = parse_eslpod_daily_body(full_text)
        elif feed.body_parser == "ENGLISHCLUB_REFERENCE":
            full_text = parse_englishclub_reference_body(full_text)
        if len(full_text) < feed.min_text_length:
            continue
        level = estimate_level(title=title, body=full_text, fallback=feed.level)
        if requested_levels and level not in requested_levels:
            continue

        pub_date = infer_publication_date(
            pub_date=rss_item.get("pubDate", ""),
            link=link,
            feed=feed,
        )
        content = item_to_content(
            feed=feed,
            title=title,
            body=full_text,
            link=link,
            guid=rss_item.get("guid", ""),
            author=rss_item.get("author", ""),
            pub_date=pub_date,
            level=level,
        )
        if content.title and body_text(content):
            content_items.append(content)
        if len(content_items) >= limit:
            break

    return content_items


def parse_rss_items(xml: str) -> list[dict[str, str]]:
    root = ET.fromstring(xml)
    return [
        {
            "title": child_text(item, "title"),
            "link": child_text(item, "link"),
            "guid": child_text(item, "guid", "id"),
            "pubDate": child_text(item, "pubDate", "published", "updated"),
            "author": child_text(item, "author", "creator"),
            "description": child_text(item, "description", "summary"),
            "content": child_text(item, "encoded", "content"),
        }
        for item in root.findall(".//item")
    ]


def child_text(element: ET.Element, *names: str) -> str:
    wanted = set(names)
    for child in list(element):
        if local_name(child.tag) in wanted:
            return "".join(child.itertext()).strip()
    return ""


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].rsplit(":", 1)[-1]


def item_to_content(
    feed: "ContentFeed",
    title: str,
    body: str,
    link: str,
    guid: str,
    author: str,
    pub_date: str,
    level: str,
) -> ContentItemResponse:
    content_id = stable_id(guid or link or f"{feed.name}:{title}:{pub_date}")
    common = {
        "id": content_id,
        "title": title,
        "type": feed.type,
        "level": level,
        "body": body,
        "source": feed.name,
        "date": pub_date,
    }

    if feed.type == "ARTICLE":
        return ContentItemResponse(**common, author=author or feed.name)
    if feed.type == "BLOG":
        return ContentItemResponse(**common, author=author or feed.name)
    if feed.type == "NEWS":
        return ContentItemResponse(**common)

    return ContentItemResponse(
        **common,
        lines=parse_dialogue_lines(body),
    )


def fetch_article_body(link: str, feed: "ContentFeed", deadline: float) -> str:
    if not link or not feed.fetch_detail_page or is_deadline_expired(deadline):
        return ""

    try:
        page = download_text(link, timeout=request_timeout(deadline))
    except Exception:
        return ""

    if feed.body_parser == "BBC":
        return parse_bbc_body(page)
    if feed.body_parser == "BREAKING_NEWS_ENGLISH":
        return parse_breaking_news_english_body(page)
    if feed.body_parser == "VOA":
        return parse_voa_body(page)
    if feed.body_parser == "GUTENBERG":
        return parse_gutenberg_body(page, link, deadline)
    return ""


def parse_bbc_body(page: str) -> str:
    transcript = extract_bbc_transcript_html(page)
    body = transcript or page.partition("<h3><strong>Introduction</strong></h3>")[2].partition("<p><a href=")[0]
    text = clean_text(body)
    return text if len(text) > MIN_DETAIL_TEXT_LENGTH else ""


def extract_bbc_transcript_html(page: str) -> str:
    marker = re.search(
        r"<p[^>]*>\s*<strong[^>]*>\s*TRANSCRIPT\s*</strong>\s*</p>",
        page,
        flags=re.IGNORECASE | re.DOTALL,
    )
    if not marker:
        return ""

    fragment = page[marker.end():]
    end_positions = [
        match.start()
        for pattern in BBC_TRANSCRIPT_END_PATTERNS
        if (match := re.search(pattern, fragment, flags=re.IGNORECASE | re.DOTALL))
    ]
    return fragment[: min(end_positions)] if end_positions else fragment[:MAX_TRANSCRIPT_HTML_CHARS]


def parse_breaking_news_english_body(page: str) -> str:
    text = clean_text(first_group(re.search(r"<article>(.*?)</article>", page, flags=re.DOTALL)))
    return text if len(text) > MIN_DETAIL_TEXT_LENGTH else ""


def parse_voa_body(page: str) -> str:
    paragraphs = VoaBodyParser.extract(page)
    text = "\n\n".join(
        paragraph
        for paragraph in paragraphs
        if len(paragraph) >= MIN_PARAGRAPH_TEXT_LENGTH and not is_article_noise(paragraph)
    )
    return text if len(text) > MIN_DETAIL_TEXT_LENGTH else ""


def parse_gutenberg_body(page: str, link: str, deadline: float) -> str:
    text_href = first_group(
        re.search(r'href="([^"]+\.txt(?:\.utf-8)?[^"]*)"', page, flags=re.IGNORECASE)
    )
    if not text_href or is_deadline_expired(deadline):
        return ""

    try:
        raw_text = download_text(
            urljoin(link, html.unescape(text_href)),
            timeout=request_timeout(deadline),
        )
    except Exception:
        return ""

    text = clean_gutenberg_text(raw_text)
    return text[:GUTENBERG_TEXT_LIMIT].strip()


def clean_gutenberg_text(text: str) -> str:
    if not text:
        return ""

    start_match = re.search(
        r"\*\*\*\s*START OF (?:THE|THIS) PROJECT GUTENBERG EBOOK.*?\*\*\*",
        text,
        flags=re.IGNORECASE | re.DOTALL,
    )
    if start_match:
        text = text[start_match.end():]

    end_match = re.search(
        r"\*\*\*\s*END OF (?:THE|THIS) PROJECT GUTENBERG EBOOK",
        text,
        flags=re.IGNORECASE,
    )
    if end_match:
        text = text[: end_match.start()]

    lines = [line.strip() for line in text.replace("\r\n", "\n").replace("\r", "\n").splitlines()]
    lines = [line for line in lines if line]
    while lines and is_gutenberg_front_matter(lines[0]):
        lines.pop(0)
    return re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()


def is_gutenberg_front_matter(line: str) -> bool:
    normalized = line.lower().strip()
    return (
        normalized.startswith("title:")
        or normalized.startswith("author:")
        or normalized.startswith("release date:")
        or normalized.startswith("language:")
        or normalized.startswith("credits:")
        or normalized.startswith("most recently updated:")
        or normalized in {"contents", "chapter", "by"}
    )


class VoaBodyParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.capture = False
        self.capture_depth = 0
        self.text_tag: str | None = None
        self.parts: list[str] = []
        self.paragraphs: list[str] = []

    @classmethod
    def extract(cls, page: str) -> list[str]:
        parser = cls()
        parser.feed(page)
        return parser.paragraphs

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = {key: value or "" for key, value in attrs}
        classes = set(attributes.get("class", "").split())
        if not self.capture and (
            attributes.get("id") == "article-content" or "wsw" in classes
        ):
            self.capture = True
            self.capture_depth = 1
        elif self.capture:
            self.capture_depth += 1

        if self.capture and tag in {"p", "h2", "h3"}:
            self.text_tag = tag
            self.parts = []

    def handle_endtag(self, tag: str) -> None:
        if self.capture and self.text_tag == tag:
            text = " ".join("".join(self.parts).split())
            if text:
                self.paragraphs.append(text)
            self.text_tag = None
            self.parts = []

        if self.capture:
            self.capture_depth -= 1
            if self.capture_depth <= 0:
                self.capture = False

    def handle_data(self, data: str) -> None:
        if self.capture and self.text_tag is not None:
            self.parts.append(data)


def parse_eslpod_daily_body(text: str) -> str:
    if not text:
        return ""
    if not re.search(r"\bDaily English\b|\bDialogue/Story\b", text, flags=re.IGNORECASE):
        return ""
    body = re.split(r"\bKey Terms\b|\bWords:\b", text, maxsplit=1, flags=re.IGNORECASE)[0]
    body = re.sub(r"^Topics\s+", "", body, flags=re.IGNORECASE).strip()
    body = re.sub(r"^Daily English\s+\d+\s*[-–]\s*", "", body, flags=re.IGNORECASE).strip()
    return body if len(body) > MIN_DETAIL_TEXT_LENGTH else ""


def parse_englishclub_reference_body(text: str) -> str:
    if not text:
        return ""
    body = clean_text(text)
    body = re.sub(r"\bExamples:\s*", "\nFor example:\n", body, flags=re.IGNORECASE)
    body = re.sub(r"(?<!For )\bExample:\s*", "\nFor example:\n", body, flags=re.IGNORECASE)
    body = re.sub(r"\bMeaning:\s*", "Meaning:\n", body, flags=re.IGNORECASE)
    body = re.sub(
        r"\b(keep sb on|keep on sb)\s+",
        lambda match: f"\n{expand_englishclub_abbreviations(match.group(1))}: ",
        body,
        flags=re.IGNORECASE,
    )
    body = expand_englishclub_abbreviations(body)
    return "\n".join(line.strip() for line in body.splitlines() if line.strip())


def expand_englishclub_abbreviations(text: str) -> str:
    text = re.sub(r"\bsb\b", "somebody", text, flags=re.IGNORECASE)
    text = re.sub(r"\bsth\b", "something", text, flags=re.IGNORECASE)
    return text


def first_group(match: re.Match[str] | None) -> str:
    return match.group(1) if match else ""


def parse_dialogue_lines(text: str) -> list[DialogueLineResponse]:
    lines = [
        line.strip()
        for line in text.splitlines()
        if line.strip() and not line.strip().lower().startswith("note:")
    ]
    dialogue_lines: list[DialogueLineResponse] = []
    current_speaker: str | None = None

    for line in lines:
        if len(line) <= MAX_SPEAKER_NAME_LENGTH and SPEAKER_NAME_REGEX.fullmatch(line):
            current_speaker = line
        else:
            dialogue_lines.append(
                DialogueLineResponse(
                    speaker=current_speaker or "Narrator",
                    text=line,
                )
            )
            current_speaker = None

    cleaned_lines = [
        line
        for line in dialogue_lines
        if line.text.strip() and not is_dialogue_noise(line.text)
    ]
    return cleaned_lines or [DialogueLineResponse(speaker="Narrator", text=text)]


def is_dialogue_noise(text: str) -> bool:
    normalized = text.strip().lower()
    return (
        normalized.startswith("note:")
        or normalized.startswith("download a free")
        or normalized.startswith("try our ")
        or normalized in {"transcript", "vocabulary", "this week's question"}
    )


def is_article_noise(text: str) -> bool:
    normalized = text.strip().lower()
    return (
        normalized.startswith("no media source")
        or normalized.startswith("your browser doesn")
        or normalized.startswith("start quiz")
        or normalized.startswith("________________________________________________")
        or normalized in {"words in this story", "quiz"}
    )


def estimate_level(title: str, body: str, fallback: str) -> str:
    words = WORD_REGEX.findall(f"{title}\n{body}".lower())
    if len(words) < MIN_WORDS_FOR_LEVEL_ESTIMATE:
        return fallback

    average_word_length = sum(len(word) for word in words) / len(words)
    long_word_ratio = len([word for word in words if len(word) >= LONG_WORD_LENGTH]) / len(words)
    sentence_count = max(1, len(SENTENCE_REGEX.findall(f"{title}\n{body}")))
    average_sentence_length = len(words) / sentence_count
    advanced_word_count = len([word for word in words if word in ADVANCED_WORDS])

    if average_sentence_length >= 26 or long_word_ratio >= 0.28 or advanced_word_count >= 12:
        return "C1"
    if average_sentence_length >= 21 or average_word_length >= 5.6 or long_word_ratio >= 0.22 or advanced_word_count >= 7:
        return "B2"
    if average_sentence_length >= 15 or average_word_length >= 5.0 or long_word_ratio >= 0.14 or advanced_word_count >= 3:
        return "B1"
    if average_sentence_length >= 10 or average_word_length >= 4.4:
        return "A2"
    return "A1"


def body_text(item: ContentItemResponse) -> str:
    if item.type == "DIALOGUE":
        return "\n".join(line.text for line in item.lines)
    return item.body


def normalize_title(title: str, fallback: str) -> str:
    title = title.removeprefix("BBC Learning English - ")
    if " / " in title:
        title = title.split(" / ", 1)[1]
    return title or fallback


def is_feed_title_noise(title: str, feed: "ContentFeed") -> bool:
    normalized = title.lower()
    if feed.name == "TechCrunch AI":
        return any(phrase in normalized for phrase in TECHCRUNCH_TITLE_NOISE)
    return False


def clean_text(value: str) -> str:
    if not value:
        return ""

    text = re.sub(r"<script.*?</script>", " ", value, flags=re.DOTALL | re.IGNORECASE)
    text = re.sub(r"<style.*?</style>", " ", text, flags=re.DOTALL | re.IGNORECASE)
    text = re.sub(r"<br\s*/?>", "\n", text, flags=re.IGNORECASE)
    text = re.sub(r"</p\s*>", "\n\n", text, flags=re.IGNORECASE)
    text = re.sub(r"</h[1-6]\s*>", "\n\n", text, flags=re.IGNORECASE)
    text = re.sub(r"</li\s*>", "\n", text, flags=re.IGNORECASE)
    text = re.sub(r"<[^>]+>", " ", text)
    text = html.unescape(text).replace("\xa0", " ")
    text = "\n".join(line.strip() for line in text.splitlines() if line.strip())
    return re.sub(r"\n{3,}", "\n\n", text).strip()


def download_text(url: str, timeout: float | None = None) -> str:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "application/rss+xml, application/xml, text/xml, text/html, */*",
        },
    )
    with urllib.request.urlopen(request, timeout=timeout or REQUEST_TIMEOUT_SECONDS) as response:
        return response.read().decode("utf-8", errors="replace")


def is_deadline_expired(deadline: float) -> bool:
    return monotonic() >= deadline


def request_timeout(deadline: float) -> float:
    return min(REQUEST_TIMEOUT_SECONDS, max(1.0, deadline - monotonic()))


def stable_id(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def parsed_date_timestamp(value: str) -> float:
    try:
        return parsedate_to_datetime(value).timestamp()
    except Exception:
        return 0.0


def infer_publication_date(pub_date: str, link: str, feed: "ContentFeed") -> str:
    cleaned = clean_text(pub_date)
    if cleaned:
        return cleaned

    if feed.body_parser == "GUTENBERG":
        return rfc2822_datetime(datetime.now(timezone.utc))

    bbc_match = re.search(r"(?:ep-|/)(\d{2})(\d{2})(\d{2})(?:\D|$)", link)
    if bbc_match:
        year = 2000 + int(bbc_match.group(1))
        month = int(bbc_match.group(2))
        day = int(bbc_match.group(3))
        return rfc2822_datetime(datetime(year, month, day, tzinfo=timezone.utc))

    slash_match = re.search(r"/(20\d{2})/(\d{2})/(\d{2})/", link)
    if slash_match:
        year = int(slash_match.group(1))
        month = int(slash_match.group(2))
        day = int(slash_match.group(3))
        return rfc2822_datetime(datetime(year, month, day, tzinfo=timezone.utc))

    return ""


def rfc2822_datetime(value: datetime) -> str:
    return value.strftime("%a, %d %b %Y %H:%M:%S +0000")


def normalize_filter_values(values: list[str]) -> set[str]:
    return {value.strip().upper() for value in values if value and value.strip()}


def normalize_source_values(values: list[str]) -> set[str]:
    return {value.strip() for value in values if value and value.strip()}


def build_content_filters(items: list[ContentItemResponse]) -> ContentFiltersResponse:
    return ContentFiltersResponse(
        types=[
            ContentFilterOptionResponse(id=key, label=display_filter_label(key), count=count)
            for key, count in sorted(count_values(item.type for item in items).items())
        ],
        levels=[
            ContentFilterOptionResponse(id=key, label=key, count=count)
            for key, count in sorted(count_values(item.level for item in items).items())
        ],
        sources=[
            ContentFilterOptionResponse(id=key, label=key, count=count)
            for key, count in sorted(count_values(item.source or item.author or "Unknown" for item in items).items())
        ],
    )


def count_values(values: Iterable[str]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for value in values:
        cleaned = str(value).strip()
        if cleaned:
            counts[cleaned] = counts.get(cleaned, 0) + 1
    return counts


def display_filter_label(value: str) -> str:
    return value.lower().replace("_", " ").title()


@dataclass(frozen=True)
class ContentFeed:
    name: str
    url: str
    type: str
    level: str
    limit: int = 10
    expanded_limit: int = 50
    fetch_detail_page: bool = False
    body_parser: str = "NONE"
    min_text_length: int = 120


USER_AGENT = "Mozilla/5.0 (Linux; Android) EnglishStudy/1.0"
REQUEST_TIMEOUT_SECONDS = 15
CONTENT_FETCH_DEADLINE_SECONDS = 55
MAX_WORKERS = 8
MIN_DETAIL_TEXT_LENGTH = 120
MIN_PARAGRAPH_TEXT_LENGTH = 12
MAX_SPEAKER_NAME_LENGTH = 40
MAX_TRANSCRIPT_HTML_CHARS = 30_000
MIN_WORDS_FOR_LEVEL_ESTIMATE = 30
LONG_WORD_LENGTH = 8
GUTENBERG_TEXT_LIMIT = 6_000
SPEAKER_NAME_REGEX = re.compile(r"[A-Z][A-Za-z'-]*(?: [A-Z][A-Za-z'-]*){0,4}")
WORD_REGEX = re.compile(r"[a-z]+(?:'[a-z]+)?")
SENTENCE_REGEX = re.compile(r"[.!?]+")
BBC_TRANSCRIPT_END_PATTERNS = [
    r'<div[^>]+class="[^"]*widget-list[^"]*"',
    r'<div[^>]+class="[^"]*related-content[^"]*"',
    r'<div[^>]+class="[^"]*promo-unit[^"]*"',
    r"<h2[^>]*>\s*More episodes\s*</h2>",
]
TECHCRUNCH_TITLE_NOISE = {
    "early bird pricing",
    "founder summit",
    "disrupt",
    "last chance",
}
ADVANCED_WORDS = {
    "approximately",
    "consequence",
    "considerable",
    "controversial",
    "development",
    "environmental",
    "fundamental",
    "government",
    "infrastructure",
    "international",
    "nevertheless",
    "particularly",
    "perspective",
    "phenomenon",
    "significant",
    "substantial",
    "technology",
    "therefore",
    "unprecedented",
}

VOA_FEEDS = [
    ContentFeed("VOA Learning English", "https://learningenglish.voanews.com/api/", "ARTICLE", "B1", limit=6, expanded_limit=20, fetch_detail_page=True, body_parser="VOA"),
    ContentFeed("VOA All About America", "https://learningenglish.voanews.com/api/zbmroml-vomx-tpeqboo_", "ARTICLE", "B1", fetch_detail_page=True, body_parser="VOA"),
    ContentFeed("VOA As It Is", "https://learningenglish.voanews.com/api/zkm-ql-vomx-tpej-rqi", "NEWS", "B1", fetch_detail_page=True, body_parser="VOA"),
    ContentFeed("VOA Arts & Culture", "https://learningenglish.voanews.com/api/zpyp_l-vomx-tpe_rym", "ARTICLE", "B1", fetch_detail_page=True, body_parser="VOA"),
    ContentFeed("VOA American Stories", "https://learningenglish.voanews.com/api/zyg__l-vomx-tpetmty", "ARTICLE", "B2", fetch_detail_page=True, body_parser="VOA"),
    ContentFeed("VOA Everyday Grammar", "https://learningenglish.voanews.com/api/zoroqql-vomx-tpeptpqq", "ARTICLE", "A2", fetch_detail_page=True, body_parser="VOA"),
    ContentFeed("VOA English in a Minute", "https://learningenglish.voanews.com/api/zjk-rl-vomx-tpebpqqo", "BLOG", "A2"),
]

CONTENT_FEEDS = VOA_FEEDS + [
    ContentFeed("BBC 6 Minute English", "https://www.bbc.co.uk/learningenglish/english/features/6-minute-english/rss", "DIALOGUE", "B1", limit=8, expanded_limit=24, fetch_detail_page=True, body_parser="BBC"),
    ContentFeed("BBC The English We Speak", "https://www.bbc.co.uk/learningenglish/english/features/the-english-we-speak/rss", "DIALOGUE", "A2", limit=8, expanded_limit=24, fetch_detail_page=True, body_parser="BBC"),
    ContentFeed("ESLPod Daily English", "https://www.eslpod.com/feed.xml", "DIALOGUE", "B1", limit=5, expanded_limit=20, body_parser="ESLPOD_DAILY"),
    ContentFeed("Breaking News English", "https://breakingnewsenglish.com/bne.xml", "NEWS", "B2", limit=12, expanded_limit=50, fetch_detail_page=True, body_parser="BREAKING_NEWS_ENGLISH"),
    ContentFeed("TechCrunch AI", "https://techcrunch.com/category/artificial-intelligence/feed/", "NEWS", "C1", limit=10, expanded_limit=30, min_text_length=100),
    ContentFeed("The Decoder AI", "https://the-decoder.com/feed/", "NEWS", "B2", limit=10, expanded_limit=30, min_text_length=180),
    ContentFeed("VentureBeat AI", "https://venturebeat.com/category/ai/feed/", "ARTICLE", "C1", limit=3, expanded_limit=10, min_text_length=800),
    ContentFeed("The Economist Finance & Economics", "https://www.economist.com/finance-and-economics/rss.xml", "NEWS", "C1", limit=8, expanded_limit=30, min_text_length=70),
    ContentFeed("Project Gutenberg Today", "https://www.gutenberg.org/cache/epub/feeds/today.rss", "ARTICLE", "B2", limit=4, expanded_limit=12, fetch_detail_page=True, body_parser="GUTENBERG"),
    ContentFeed("EnglishClub Idiom of the Day", "https://www.englishclub.com/ref/idiom-of-the-day.xml", "BLOG", "B2", limit=1, body_parser="ENGLISHCLUB_REFERENCE", min_text_length=60),
    ContentFeed("EnglishClub Phrasal Verb of the Day", "https://www.englishclub.com/ref/phrasal-verb-of-the-day.xml", "BLOG", "B1", limit=1, body_parser="ENGLISHCLUB_REFERENCE", min_text_length=60),
    ContentFeed("EnglishClub Saying of the Day", "https://www.englishclub.com/ref/saying-of-the-day.xml", "BLOG", "B2", limit=1, body_parser="ENGLISHCLUB_REFERENCE", min_text_length=60),
]
