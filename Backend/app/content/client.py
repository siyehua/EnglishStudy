from __future__ import annotations

import concurrent.futures
import hashlib
import json
import re
import urllib.request
from collections.abc import Iterable
from pathlib import Path
from time import monotonic

from app.schemas import (
    ContentFilterOptionResponse,
    ContentFiltersResponse,
    ContentItemResponse,
    DialogueLineResponse,
)


class ContentFetchError(Exception):
    pass


ENGLISH_POD_SOURCE_NAME = "EnglishPod"
ENGLISH_POD_INDEX_URL = (
    "https://cdn.jsdelivr.net/gh/bitter999/EnglishPod@main/data_index.js"
)
ENGLISH_POD_LESSON_URL_TEMPLATE = (
    "https://cdn.jsdelivr.net/gh/bitter999/EnglishPod@main/data/lesson_{number}.json"
)
LINYUANZKY_BASE = "https://cdn.jsdelivr.net/gh/linyuanzky/englishpod365@main/"
AUDIO_MAP_PATH = Path(__file__).resolve().parent / "englishpod_audio_map.json"

USER_AGENT = "Mozilla/5.0 (Linux; Android) EnglishStudy/1.0"
REQUEST_TIMEOUT_SECONDS = 15
FETCH_DEADLINE_SECONDS = 55
MAX_WORKERS = 8
DEFAULT_LIMIT = 20
EXPANDED_LIMIT = 100

LETTER_LEVEL_MAP = {
    "B": "A2",  # Elementary
    "C": "B1",  # Intermediate
    "D": "B2",  # Upper Intermediate
    "E": "C1",  # Advanced
    "F": "C1",  # Advanced Media
}

# Each lesson is split into three independent courses.
SECTION_ORDER = [
    ("dialogue", "对话"),
    ("explanation", "讲解"),
    ("review", "回顾"),
]


def load_audio_map() -> dict[str, dict[str, str | None]]:
    try:
        return json.loads(AUDIO_MAP_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


AUDIO_MAP = load_audio_map()


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

        if requested_types and "DIALOGUE" not in requested_types:
            return [], build_content_filters([])
        if requested_sources and ENGLISH_POD_SOURCE_NAME not in requested_sources:
            return [], build_content_filters([])

        try:
            index = download_lesson_index()
        except Exception:
            return [], build_content_filters([])

        lesson_numbers = sorted(index)
        limit = EXPANDED_LIMIT if fetch_more else DEFAULT_LIMIT
        selected = lesson_numbers[:limit]

        deadline = monotonic() + FETCH_DEADLINE_SECONDS
        by_number: dict[int, list[ContentItemResponse]] = {}
        executor = concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS)
        try:
            future_to_number = {
                executor.submit(
                    fetch_lesson_items_safely,
                    number,
                    requested_levels,
                    deadline,
                ): number
                for number in selected
            }
            done, pending = concurrent.futures.wait(
                future_to_number,
                timeout=max(0.1, deadline - monotonic()),
            )
            for future in done:
                items = future.result()
                if items:
                    by_number[future_to_number[future]] = items
            for future in pending:
                future.cancel()
        finally:
            executor.shutdown(wait=False, cancel_futures=True)

        items = [
            item
            for number in selected
            for item in by_number.get(number, [])
        ]
        return items, build_content_filters(items)


def download_lesson_index() -> dict[int, dict[str, str]]:
    text = download_text(ENGLISH_POD_INDEX_URL)
    marker = text.find("=")
    if marker < 0:
        raise ContentFetchError("Invalid EnglishPod index format.")

    payload = text[marker + 1:].strip()
    if payload.endswith(";"):
        payload = payload[:-1].rstrip()

    raw = json.loads(payload)
    index: dict[int, dict[str, str]] = {}
    for number, metadata in raw.items():
        try:
            index[int(number)] = metadata
        except (TypeError, ValueError):
            continue
    return index


def fetch_lesson_items_safely(
    number: int,
    requested_levels: set[str],
    deadline: float,
) -> list[ContentItemResponse]:
    try:
        return fetch_lesson_items(number, requested_levels, deadline)
    except Exception:
        return []


def fetch_lesson_items(
    number: int,
    requested_levels: set[str],
    deadline: float,
) -> list[ContentItemResponse]:
    if is_deadline_expired(deadline):
        return []

    text = download_text(
        ENGLISH_POD_LESSON_URL_TEMPLATE.format(number=number),
        timeout=request_timeout(deadline),
    )
    lesson = json.loads(text)
    return lesson_to_content_items(number, lesson, requested_levels)


def lesson_to_content_items(
    number: int,
    lesson: dict,
    requested_levels: set[str] | None = None,
) -> list[ContentItemResponse]:
    raw_title = str(lesson.get("title") or f"Lesson {number}").strip()
    base_title = format_lesson_title(number, raw_title)
    level = englishpod_level(title=raw_title, audio=str(lesson.get("audio") or ""))
    if requested_levels and level not in requested_levels:
        return []

    content = lesson.get("content") or []
    section_marks = annotate_sections(content)

    _, full_url, _ = englishpod_audio_urls(number)

    items: list[ContentItemResponse] = []
    for section_name, label in SECTION_ORDER:
        section_items = [
            (item, mark)
            for item, mark in zip(content, section_marks)
            if mark == section_name and str(item.get("text") or "").strip()
        ]
        if not section_items:
            continue

        texts = [str(item.get("text") or "").strip() for item, _ in section_items]
        body = "\n".join(texts)

        start = min(float(item.get("start") or 0.0) for item, _ in section_items)
        end = max(
            float(item.get("end") or item.get("start") or 0.0)
            for item, _ in section_items
        )

        lines = [DialogueLineResponse(speaker="Narrator", text=text) for text in texts]
        items.append(
            ContentItemResponse(
                id=stable_id(f"englishpod-{number}-{section_name}"),
                title=f"{base_title} · {label}",
                type="DIALOGUE",
                level=level,
                body=body,
                author=None,
                source=ENGLISH_POD_SOURCE_NAME,
                date="",
                lines=lines,
                audioUrl=full_url,
                audioStart=start,
                audioEnd=end,
            )
        )
    return items


def englishpod_level(title: str, audio: str) -> str:
    match = re.search(r"englishpod_([A-Za-z])\d+", audio)
    if match:
        return LETTER_LEVEL_MAP.get(match.group(1).upper(), "B1")

    lowered = title.lower()
    if "upper" in lowered and "intermediate" in lowered:
        return "B2"
    if "elementary" in lowered or "beginner" in lowered:
        return "A2"
    if "intermediate" in lowered:
        return "B1"
    if "advanced" in lowered:
        return "C1"
    return "B1"


def format_lesson_title(number: int, raw_title: str) -> str:
    without_number = re.sub(r"^\d+\s+", "", raw_title.strip())
    if " - " in without_number:
        subtitle = without_number.split(" - ", 1)[1].strip()
    else:
        subtitle = without_number
    return f"{number}: {subtitle}"


DIALOGUE_REPEAT_HINTS = ("again", "one more", "third", "second", "another time")
EXPLANATION_HINTS = (
    "the first one",
    "i have another",
    "interesting expression",
    "let's listen to some examples",
    "let's take a look at",
)


def classify_section(text: str) -> str | None:
    lowered = text.lower()
    if "fluency builder" in lowered:
        return "review"
    if "language takeaway" in lowered or "vocabulary preview" in lowered:
        return "explanation"
    if "dialogue" in lowered:
        if any(
            marker in lowered
            for marker in (
                "in this dialogue",
                "in the dialogue",
                "about this dialogue",
                "what happens in this dialogue",
                "makes you",
            )
        ):
            return None
        if any(
            action in lowered
            for action in ("listen", "take a look", "hear", "ready to", "we are ready")
        ):
            if any(hint in lowered for hint in DIALOGUE_REPEAT_HINTS):
                return "review"
            return "dialogue"
    if any(hint in lowered for hint in EXPLANATION_HINTS):
        return "explanation"
    return None


def annotate_sections(content: list[dict]) -> list[str]:
    sections: list[str] = []
    current = "explanation"
    for item in content:
        classified = classify_section(str(item.get("text") or ""))
        if classified:
            current = classified
        sections.append(current)
    return sections


def englishpod_audio_urls(number: int) -> tuple[str | None, str | None, str | None]:
    """Return (dialogue, full, review) audio URLs for a lesson number."""
    entry = AUDIO_MAP.get(str(number))
    if not entry:
        return None, None, None
    dialogue = entry.get("dialogue")
    full = entry.get("full")
    review = entry.get("review")
    return (
        LINYUANZKY_BASE + dialogue if dialogue else None,
        LINYUANZKY_BASE + full if full else None,
        LINYUANZKY_BASE + review if review else None,
    )


def download_text(url: str, timeout: float | None = None) -> str:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "application/json, text/plain, text/javascript, */*",
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
