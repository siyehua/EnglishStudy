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
ENGLISH_POD_RAW_BASE = "https://cdn.jsdelivr.net/gh/bitter999/EnglishPod@main/"
ENGLISH_POD_LESSON_URL_TEMPLATE = (
    "https://cdn.jsdelivr.net/gh/bitter999/EnglishPod@main/data/lesson_{number}.json"
)
LINYUANZKY_BASE = "https://cdn.jsdelivr.net/gh/linyuanzky/englishpod365@main/"
AUDIO_MAP_PATH = Path(__file__).resolve().parent / "englishpod_audio_map.json"
TIMESTAMPS_DIR = Path(__file__).resolve().parent / "englishpod_timestamps"

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


def load_whisper_segments(number: int) -> list[dict] | None:
    """Sentence-level timestamps produced by Whisper alignment, if available."""
    path = TIMESTAMPS_DIR / f"{number}.json"
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None
    if not isinstance(data, list) or not data:
        return None
    return merge_segments_into_sentences(data)


SENTENCE_END_PATTERN = re.compile(r"[.!?][\"')\u201d\u2019\]]*$")


def merge_segments_into_sentences(segments: list[dict]) -> list[dict]:
    """Whisper splits on pauses, not on sentence boundaries, so a single
    sentence can arrive as several segments (e.g. "...involved, the" +
    "waiter and the customer."). Merge fragments until the text ends with
    terminal punctuation.
    """
    merged: list[dict] = []
    current: dict | None = None

    for seg in segments:
        text = str(seg.get("text") or "").strip()
        if not text:
            continue
        start = float(seg.get("start") or 0.0)
        end = float(seg.get("end") or start)

        if current is None:
            current = {"text": text, "start": start, "end": end}
        else:
            current["text"] = f"{current['text']} {text}"
            current["end"] = end

        if SENTENCE_END_PATTERN.search(text):
            merged.append(current)
            current = None

    if current is not None:
        merged.append(current)
    return merged


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
    _, full_url, _ = englishpod_audio_urls(number)

    # Use the downloaded transcript as-is (each line already has accurate text,
    # Chinese translation and timestamps). No re-recognition, no re-cutting.
    lines: list[DialogueLineResponse] = []
    for item in content:
        text = str(item.get("text") or "").strip()
        if not text:
            continue
        item_start = float(item.get("start") or 0.0)
        item_end = float(item.get("end") or item_start)
        lines.append(
            DialogueLineResponse(
                speaker="Narrator",
                text=text,
                trans=str(item.get("trans") or "").strip(),
                start=item_start,
                end=item_end,
                audioUrl=(
                    f"/ting/segment?lesson={number}"
                    f"&start={item_start:.3f}&end={item_end:.3f}"
                ),
            )
        )
    if not lines:
        return []

    body = "\n".join(line.text for line in lines)
    start = min(line.start for line in lines)
    end = max(line.end for line in lines)
    return [
        ContentItemResponse(
            id=stable_id(f"englishpod-{number}"),
            title=base_title,
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
    ]


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

# Host commentary that follows the dialogue: these lines reference the characters
# in the third person, so they belong to the explanation, not the dialogue.
HOST_COMMENTARY_HINTS = (
    "what a waiter",
    "what a customer",
    "this customer",
    "this waiter",
    "i can't blame him",
    "i cant blame him",
)


def is_host_commentary(text: str) -> bool:
    lowered = text.lower()
    return any(hint in lowered for hint in HOST_COMMENTARY_HINTS)


def is_dialogue_cue(text: str) -> str | None:
    """Detect a host cue that starts/restarts the dialogue.

    Returns the section the *following* lines belong to ("dialogue" for the
    first run, "review" for a repeat), or None if this is not a cue.
    """
    lowered = text.lower()
    if "dialogue" not in lowered:
        return None
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
    return None


def classify_section(text: str) -> str | None:
    lowered = text.lower()
    if "fluency builder" in lowered:
        return "review"
    if "language takeaway" in lowered or "vocabulary preview" in lowered:
        return "explanation"
    if is_dialogue_cue(lowered) is not None:
        # The cue line itself is spoken by the host, so it stays in explanation.
        return "explanation"
    if any(hint in lowered for hint in EXPLANATION_HINTS):
        return "explanation"
    return None


def annotate_sections(content: list[dict]) -> list[str]:
    sections: list[str] = []
    current = "explanation"
    for item in content:
        text = str(item.get("text") or "")
        lowered = text.lower()

        cue = is_dialogue_cue(lowered)
        if cue is not None:
            # Host cue line: keep it in the explanation, then switch state
            # so the actual character dialogue that follows is tagged correctly.
            sections.append("explanation")
            current = cue
            continue

        if is_host_commentary(lowered):
            # Commentary after the dialogue: it is the host talking again.
            current = "explanation"
            sections.append(current)
            continue

        classified = classify_section(text)
        if classified:
            current = classified
        sections.append(current)
    return sections


SENTENCE_SPLIT_PATTERN = re.compile(r"[^.!?]+[.!?]*")


def split_sentences_with_times(
    text: str,
    start: float,
    end: float,
) -> list[tuple[str, float, float]]:
    """Split a transcript block into sentences and distribute its time range."""
    cleaned = text.strip()
    if not cleaned:
        return []
    sentences = [
        s.strip()
        for s in SENTENCE_SPLIT_PATTERN.findall(cleaned)
        if s.strip()
    ]
    if len(sentences) <= 1:
        return [(cleaned, start, end)]

    total_chars = sum(len(s) for s in sentences)
    duration = max(0.0, end - start)
    result: list[tuple[str, float, float]] = []
    cursor = start
    for sentence in sentences:
        ratio = len(sentence) / total_chars if total_chars else 0.0
        sentence_end = cursor + duration * ratio
        result.append((sentence, cursor, sentence_end))
        cursor = sentence_end
    return result


def englishpod_original_audio_url(number: int) -> str | None:
    """Original full-lesson audio used for the Whisper timestamps."""
    try:
        index = download_lesson_index()
    except Exception:
        return None
    meta = index.get(number)
    if not meta:
        return None
    rel = str(meta.get("audio") or "").lstrip("./")
    return ENGLISH_POD_RAW_BASE + rel if rel else None


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
