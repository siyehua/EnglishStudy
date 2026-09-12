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
    ContentSectionResponse,
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
ENGLISH_POD_RAW_BASE = "https://cdn.jsdelivr.net/gh/bitter999/EnglishPod@main/"
LINYUANZKY_BASE = "https://cdn.jsdelivr.net/gh/linyuanzky/englishpod365@main/"
AUDIO_MAP_PATH = Path(__file__).resolve().parent / "englishpod_audio_map.json"


def load_audio_map() -> dict[str, dict[str, str | None]]:
    try:
        return json.loads(AUDIO_MAP_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


AUDIO_MAP = load_audio_map()


def englishpod_audio_urls(number: int) -> tuple[str | None, str | None]:
    """Return (full audio URL, dialogue audio URL) for a lesson number."""
    entry = AUDIO_MAP.get(str(number))
    if not entry:
        return None, None
    full = entry.get("full")
    dialogue = entry.get("dialogue")
    return (
        LINYUANZKY_BASE + full if full else None,
        LINYUANZKY_BASE + dialogue if dialogue else None,
    )

USER_AGENT = "Mozilla/5.0 (Linux; Android) EnglishStudy/1.0"
REQUEST_TIMEOUT_SECONDS = 15
FETCH_DEADLINE_SECONDS = 55
MAX_WORKERS = 8
DEFAULT_LIMIT = 20
EXPANDED_LIMIT = 100

# EnglishPod audio filenames carry a level letter (B/C/D/E/F). For lessons
# without a letter the title is used as a fallback.
LETTER_LEVEL_MAP = {
    "B": "A2",  # Elementary
    "C": "B1",  # Intermediate
    "D": "B2",  # Upper Intermediate
    "E": "C1",  # Advanced
    "F": "C1",  # Advanced Media
}


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
        by_number: dict[int, ContentItemResponse] = {}
        executor = concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS)
        try:
            future_to_number = {
                executor.submit(
                    fetch_lesson_safely,
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
                item = future.result()
                if item is not None:
                    by_number[future_to_number[future]] = item
            for future in pending:
                future.cancel()
        finally:
            executor.shutdown(wait=False, cancel_futures=True)

        items = [by_number[number] for number in selected if number in by_number]
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


def fetch_lesson_safely(
    number: int,
    requested_levels: set[str],
    deadline: float,
) -> ContentItemResponse | None:
    try:
        return fetch_lesson(number, requested_levels, deadline)
    except Exception:
        return None


def fetch_lesson(
    number: int,
    requested_levels: set[str],
    deadline: float,
) -> ContentItemResponse | None:
    if is_deadline_expired(deadline):
        return None

    text = download_text(
        ENGLISH_POD_LESSON_URL_TEMPLATE.format(number=number),
        timeout=request_timeout(deadline),
    )
    lesson = json.loads(text)
    return lesson_to_content(number, lesson, requested_levels)


def lesson_to_content(
    number: int,
    lesson: dict,
    requested_levels: set[str] | None = None,
) -> ContentItemResponse | None:
    raw_title = str(lesson.get("title") or f"Lesson {number}").strip()
    title = format_lesson_title(number, raw_title)
    audio = str(lesson.get("audio") or "")
    level = englishpod_level(title=raw_title, audio=audio)
    if requested_levels and level not in requested_levels:
        return None

    content = lesson.get("content") or []
    sections = annotate_sections(content)
    lines: list[DialogueLineResponse] = []
    for index, item in enumerate(content):
        text = str(item.get("text") or "").strip()
        if not text:
            continue
        lines.append(
            DialogueLineResponse(
                speaker="Narrator",
                text=text,
                trans=str(item.get("trans") or "").strip(),
                section=sections[index] if index < len(sections) else "",
                start=float(item.get("start") or 0.0),
                end=float(item.get("end") or 0.0),
            )
        )
    body = "\n".join(line.text for line in lines)
    if not body:
        return None

    full_url, dialogue_url = englishpod_audio_urls(number)
    return ContentItemResponse(
        id=stable_id(f"englishpod-{number}"),
        title=title,
        type="DIALOGUE",
        level=level,
        body=body,
        author=None,
        source=ENGLISH_POD_SOURCE_NAME,
        date="",
        lines=lines,
        audioUrl=full_url or englishpod_audio_url(str(lesson.get("audio") or "")),
        dialogueAudioUrl=dialogue_url,
        sections=build_sections(content, sections),
    )


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


def build_sections(
    content: list[dict],
    section_marks: list[str],
) -> list[ContentSectionResponse]:
    result: list[ContentSectionResponse] = []
    current_name: str | None = None
    current_start = 0.0
    current_end = 0.0

    for item, section in zip(content, section_marks):
        start = float(item.get("start") or 0.0)
        end = float(item.get("end") or start)
        if section != current_name:
            if current_name is not None:
                result.append(
                    ContentSectionResponse(
                        name=current_name,
                        start=current_start,
                        end=current_end,
                    )
                )
            current_name = section
            current_start = start
            current_end = end
        else:
            current_end = max(current_end, end)

    if current_name is not None:
        result.append(
            ContentSectionResponse(
                name=current_name,
                start=current_start,
                end=current_end,
            )
        )
    return result


def englishpod_audio_url(audio: str) -> str | None:
    path = audio.strip()
    if not path:
        return None
    if path.startswith("./"):
        path = path[2:]
    elif path.startswith("/"):
        path = path.lstrip("/")
    if not path:
        return None
    return ENGLISH_POD_RAW_BASE + path


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
