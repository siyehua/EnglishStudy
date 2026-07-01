from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request

from app.config import get_config_value
from app.schemas import WordPronunciationResponse


class DictionaryClient:
    def lookup(self, word: str) -> WordPronunciationResponse:
        normalized = normalize_word(word)
        if not normalized:
            return WordPronunciationResponse(
                word=word,
                normalized="",
                phonetic=None,
                audioUrl=None,
                source="dictionaryapi.dev",
                found=False,
            )

        url = f"{API_URL}/{urllib.parse.quote(normalized)}"
        request = urllib.request.Request(
            url,
            headers={"User-Agent": "EnglishStudyBackend/1.0"},
        )

        try:
            with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return enrich_with_phonetic_fallback(not_found(word, normalized))
            raise

        return enrich_with_phonetic_fallback(
            parse_dictionary_response(word=word, normalized=normalized, payload=payload)
        )


def parse_dictionary_response(
    word: str,
    normalized: str,
    payload: object,
) -> WordPronunciationResponse:
    entries = payload if isinstance(payload, list) else []
    phonetic: str | None = None
    audio_url: str | None = None

    for entry in entries:
        if not isinstance(entry, dict):
            continue
        if phonetic is None:
            phonetic = clean_text(entry.get("phonetic"))

        phonetics = entry.get("phonetics")
        if not isinstance(phonetics, list):
            continue

        for item in phonetics:
            if not isinstance(item, dict):
                continue
            item_phonetic = clean_text(item.get("text"))
            item_audio = clean_text(item.get("audio"))
            if phonetic is None and item_phonetic is not None:
                phonetic = item_phonetic
            if audio_url is None and item_audio is not None:
                audio_url = item_audio
            if phonetic is not None and audio_url is not None:
                break

        if phonetic is not None and audio_url is not None:
            break

    return WordPronunciationResponse(
        word=word,
        normalized=normalized,
        phonetic=phonetic,
        audioUrl=audio_url,
        source="dictionaryapi.dev",
        found=phonetic is not None or audio_url is not None,
    )


def not_found(word: str, normalized: str) -> WordPronunciationResponse:
    return WordPronunciationResponse(
        word=word,
        normalized=normalized,
        phonetic=None,
        audioUrl=None,
        source="dictionaryapi.dev",
        found=False,
    )


def enrich_with_phonetic_fallback(
    response: WordPronunciationResponse,
) -> WordPronunciationResponse:
    if response.phonetic is not None or not response.normalized:
        return response

    fallback = lookup_deepseek_phonetic(response.normalized)
    if fallback is None:
        return response

    phonetic, source = fallback
    return WordPronunciationResponse(
        word=response.word,
        normalized=response.normalized,
        phonetic=phonetic,
        audioUrl=response.audioUrl,
        source=f"{response.source}+{source}",
        found=True,
    )


def lookup_deepseek_phonetic(word: str) -> tuple[str, str] | None:
    api_key = get_config_value("DEEPSEEK_API_KEY")
    if not api_key:
        return None

    model = get_config_value("DEEPSEEK_MODEL", DEFAULT_DEEPSEEK_MODEL)
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": IPA_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": json.dumps({"word": word}, ensure_ascii=False),
            },
        ],
        "thinking": {"type": "disabled"},
        "response_format": {"type": "json_object"},
        "temperature": 0,
        "max_tokens": 80,
    }
    request = urllib.request.Request(
        url=deepseek_chat_completion_url(),
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "User-Agent": "EnglishStudyBackend/1.0",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=DEEPSEEK_TIMEOUT_SECONDS) as response:
            response_payload = json.loads(response.read().decode("utf-8"))
    except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError):
        return None

    phonetic = parse_phonetic_content(extract_message_content(response_payload))
    if phonetic is None:
        return None
    return phonetic, f"deepseek:{model}"


def extract_message_content(payload: object) -> str | None:
    if not isinstance(payload, dict):
        return None
    choices = payload.get("choices")
    if not isinstance(choices, list) or not choices:
        return None
    first_choice = choices[0]
    if not isinstance(first_choice, dict):
        return None
    message = first_choice.get("message")
    if not isinstance(message, dict):
        return None
    return clean_text(message.get("content"))


def parse_phonetic_content(content: str | None) -> str | None:
    if not content:
        return None
    try:
        payload = json.loads(content)
    except json.JSONDecodeError:
        return normalize_phonetic(content)

    if isinstance(payload, dict):
        for key in ("phonetic", "ipa", "pronunciation"):
            phonetic = normalize_phonetic(payload.get(key))
            if phonetic is not None:
                return phonetic
    return None


def normalize_phonetic(value: object) -> str | None:
    text = clean_text(value)
    if text is None:
        return None

    cleaned = text.strip().strip("`'\"")
    if not cleaned or cleaned.lower() in {"n/a", "na", "none", "null", "unknown"}:
        return None
    if len(cleaned) > MAX_PHONETIC_LENGTH:
        return None
    if cleaned.startswith("/") and cleaned.endswith("/"):
        return cleaned
    return f"/{cleaned.strip('/')}/"


def deepseek_chat_completion_url() -> str:
    base_url = get_config_value("DEEPSEEK_BASE_URL", DEFAULT_DEEPSEEK_BASE_URL).strip().rstrip("/")
    return f"{base_url}/chat/completions"


def clean_text(value: object) -> str | None:
    if not isinstance(value, str):
        return None
    cleaned = value.strip()
    return cleaned if cleaned else None


def normalize_word(word: str) -> str:
    return word.strip().lower().replace("\u2019", "'")


API_URL = "https://api.dictionaryapi.dev/api/v2/entries/en"
REQUEST_TIMEOUT_SECONDS = 8
DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com"
DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-flash"
DEEPSEEK_TIMEOUT_SECONDS = 12
MAX_PHONETIC_LENGTH = 80
IPA_SYSTEM_PROMPT = """
You return IPA pronunciations for an English learning app.
Return only a JSON object with one key: phonetic.
The phonetic value must be the common American English IPA for the input word.
Wrap the IPA in forward slashes, for example {"phonetic": "/əˈmɛrɪkə/"}.
If the input is not an English word or proper noun with a common pronunciation, return {"phonetic": ""}.
""".strip()
