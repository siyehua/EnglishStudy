from __future__ import annotations

import json
import re
import urllib.error
import urllib.request

from app.config import get_config_value
from app.dictionary.client import normalize_word
from app.schemas import WordPhonicsChunk, WordPhonicsResponse, WordPhonicsSegment, WordPhonicsSpan

try:
    import pronouncing
except ImportError:  # pragma: no cover
    pronouncing = None

try:
    import wordninja
except ImportError:  # pragma: no cover
    wordninja = None


class WordPhonicsClient:
    def __init__(self, enable_llm: bool = True) -> None:
        self._enable_llm = enable_llm

    def lookup(self, word: str, sentence: str = "", ipa: str | None = None) -> WordPhonicsResponse:
        normalized = normalize_phonics_word(word)
        if not normalized:
            return not_found(word=word, normalized="", message="No English word was provided.")

        if pronouncing is None:
            return not_found(
                word=word,
                normalized=normalized,
                message="Local pronunciation dictionaries are not installed. Run pip install -r requirements.txt.",
            )

        phones_string = select_pronunciation(normalized, ipa)
        if not phones_string:
            return not_found(
                word=word,
                normalized=normalized,
                message="No local CMUdict pronunciation was found for this word.",
        )

        phonemes = phones_string.split()
        word_ipa = preferred_display_ipa(ipa) or phones_to_ipa(phonemes)
        chunks = self.memory_chunks(
            word=word,
            normalized=normalized,
            sentence=sentence,
            ipa=word_ipa,
            phonemes=phonemes,
        )
        segment_texts = [chunk.text for chunk in chunks]

        return WordPhonicsResponse(
            word=word,
            normalized=normalized,
            ipa=word_ipa,
            phonemes=phonemes,
            segments=[
                WordPhonicsSegment(
                    text=chunk.text,
                    phonemes=chunk.phonemes,
                    ipa=chunk.ipa,
                )
                for chunk in chunks
            ],
            chunks=chunks,
            note=chunk_note(chunks, normalized),
            verification="matched" if len(chunks) > 1 else "whole_word",
            source="cmudict+deepseek+wordninja" if self._enable_llm else "cmudict+wordninja",
            found=True,
        )

    def memory_chunks(
        self,
        word: str,
        normalized: str,
        sentence: str,
        ipa: str,
        phonemes: list[str],
    ) -> list[WordPhonicsChunk]:
        if self._enable_llm:
            chunks = lookup_llm_memory_chunks(
                word=word,
                normalized=normalized,
                sentence=sentence,
                ipa=ipa,
                phonemes=phonemes,
            )
            if chunks:
                return chunks

        segment_texts = pronunciation_segments(normalized)
        segment_phones = split_phonemes_into_segments(
            phonemes=phonemes,
            target_count=len(segment_texts),
            segment_texts=segment_texts,
        )
        if len(segment_phones) != len(segment_texts):
            segment_texts = [normalized]
            segment_phones = [phonemes]
        return [
            WordPhonicsChunk(
                text=text,
                ipa=phones_to_ipa(phones),
                phonemes=phones,
                spans=default_spans(text),
                silent=False,
                stress=stress_label(phones),
                rule="按整词标准读音记忆。" if len(segment_texts) == 1 else "",
            )
            for text, phones in zip(segment_texts, segment_phones)
        ]


def normalize_phonics_word(word: str) -> str:
    normalized = normalize_word(word)
    match = re.search(r"[a-z]+(?:'[a-z]+)?", normalized)
    return match.group(0) if match else ""


def not_found(word: str, normalized: str, message: str) -> WordPhonicsResponse:
    return WordPhonicsResponse(
        word=word,
        normalized=normalized,
        ipa=None,
        phonemes=[],
        segments=[],
        chunks=[],
        note="",
        verification="not_found",
        source="cmudict+deepseek+wordninja",
        found=False,
        message=message,
    )


def lookup_llm_memory_chunks(
    word: str,
    normalized: str,
    sentence: str,
    ipa: str,
    phonemes: list[str],
) -> list[WordPhonicsChunk]:
    api_key = get_config_value("DEEPSEEK_API_KEY")
    if not api_key:
        return []

    payload = {
        "model": get_config_value("DEEPSEEK_MODEL", DEFAULT_MODEL),
        "messages": [
            {"role": "system", "content": PHONICS_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": build_phonics_user_prompt(
                    word=normalized,
                    sentence=sentence,
                    ipa=ipa,
                    phonemes=phonemes,
                ),
            },
        ],
        "thinking": {"type": "disabled"},
        "response_format": {"type": "json_object"},
        "temperature": 0.1,
        "max_tokens": 900,
    }
    request = urllib.request.Request(
        url=chat_completion_url(),
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "User-Agent": "EnglishStudyBackend/1.0",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            response_payload = json.loads(response.read().decode("utf-8"))
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, json.JSONDecodeError):
        return []

    content = extract_message_content(response_payload)
    chunks = parse_memory_chunks(content)
    if not chunks_are_valid(chunks, normalized):
        return []
    return chunks


def build_phonics_user_prompt(
    word: str,
    sentence: str,
    ipa: str,
    phonemes: list[str],
) -> str:
    request = {
        "word": word,
        "sentence": sentence.strip(),
        "ipa": ipa,
        "phonemes": phonemes,
    }
    return (
        "请根据下面 JSON 输入，把英文单词切成适合初学者记忆的彩色拼读块。"
        "必须只返回 JSON。"
        f"\n{json.dumps(request, ensure_ascii=False)}"
    )


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
    content = message.get("content")
    return content if isinstance(content, str) else None


def parse_memory_chunks(content: str | None) -> list[WordPhonicsChunk]:
    if not content:
        return []
    try:
        payload = json.loads(content)
    except json.JSONDecodeError:
        return []
    raw_chunks = payload.get("chunks") if isinstance(payload, dict) else None
    if not isinstance(raw_chunks, list):
        return []

    chunks: list[WordPhonicsChunk] = []
    for item in raw_chunks:
        if not isinstance(item, dict):
            continue
        text = clean_chunk_text(item.get("text"))
        if not text:
            continue
        chunks.append(
            with_normalized_spans(
                WordPhonicsChunk(
                    text=text,
                    ipa=clean_short_text(item.get("ipa")),
                    phonemes=[
                        clean_short_text(phoneme)
                        for phoneme in item.get("phonemes", [])
                        if clean_short_text(phoneme)
                    ]
                    if isinstance(item.get("phonemes"), list)
                    else [],
                    spans=parse_memory_spans(item.get("spans")),
                    silent=bool(item.get("silent", False)),
                    stress=clean_short_text(item.get("stress")).lower(),
                    rule=clean_rule_text(item.get("rule")),
                )
            )
        )
    return chunks[:MAX_MEMORY_CHUNKS]


def parse_memory_spans(value: object) -> list[WordPhonicsSpan]:
    if not isinstance(value, list):
        return []
    spans: list[WordPhonicsSpan] = []
    for item in value:
        if not isinstance(item, dict):
            continue
        text = clean_chunk_text(item.get("text"))
        if text:
            spans.append(WordPhonicsSpan(text=text, silent=bool(item.get("silent", False))))
    return spans


def with_normalized_spans(chunk: WordPhonicsChunk) -> WordPhonicsChunk:
    spans = chunk.spans
    if not spans or "".join(span.text for span in spans) != chunk.text:
        spans = infer_spans(chunk)
    return chunk.model_copy(update={"spans": spans})


def default_spans(text: str) -> list[WordPhonicsSpan]:
    return [WordPhonicsSpan(text=text, silent=False)] if text else []


def infer_spans(chunk: WordPhonicsChunk) -> list[WordPhonicsSpan]:
    if not chunk.text:
        return []
    if chunk.silent or not chunk.ipa.strip():
        return [WordPhonicsSpan(text=chunk.text, silent=True)]

    text = chunk.text.lower()
    silent_suffix = ""
    if text.endswith("e") and len(text) > 1 and should_mark_final_e_silent(text, chunk.ipa):
        silent_suffix = "e"

    if silent_suffix:
        audible = text[: -len(silent_suffix)]
        spans: list[WordPhonicsSpan] = []
        if audible:
            spans.append(WordPhonicsSpan(text=audible, silent=False))
        spans.append(WordPhonicsSpan(text=silent_suffix, silent=True))
        return spans
    return default_spans(text)


def should_mark_final_e_silent(text: str, ipa: str) -> bool:
    if len(text) <= 1 or not text.endswith("e"):
        return False
    body = ipa.strip().strip("/")
    if not body:
        return True
    if text.endswith(("le", "gle", "kle", "ble", "ple", "dle", "tle", "fle", "zle")):
        return body.endswith(("l", "əl", "el"))
    return not body.endswith(("e", "i", "ɪ", "ɛ", "ə", "ɝ", "ɚ"))


def chunks_are_valid(chunks: list[WordPhonicsChunk], normalized: str) -> bool:
    if not chunks:
        return False
    joined = "".join(chunk.text for chunk in chunks)
    return normalize_phonics_word(joined) == normalized


def clean_chunk_text(value: object) -> str:
    if not isinstance(value, str):
        return ""
    return re.sub(r"[^A-Za-z']", "", value).lower()[:MAX_CHUNK_TEXT_LENGTH]


def clean_short_text(value: object) -> str:
    if not isinstance(value, str):
        return ""
    return " ".join(value.strip().split())[:MAX_SHORT_TEXT_LENGTH]


def clean_rule_text(value: object) -> str:
    if not isinstance(value, str):
        return ""
    return " ".join(value.strip().split())[:MAX_RULE_TEXT_LENGTH]


def chunk_note(chunks: list[WordPhonicsChunk], normalized: str) -> str:
    if len(chunks) <= 1:
        return "这个词先按整词发音记忆。"
    if any(chunk.silent or any(span.silent for span in chunk.spans) for chunk in chunks):
        return "灰色字母表示不发音或弱化到几乎听不出来。"
    return "颜色用于对应拼写和下面的音标。"


def stress_label(phonemes: list[str]) -> str:
    if any(phoneme.endswith("1") for phoneme in phonemes):
        return "primary"
    if any(phoneme.endswith("2") for phoneme in phonemes):
        return "secondary"
    if any(is_vowel(phoneme) for phoneme in phonemes):
        return "unstressed"
    return ""


def chat_completion_url() -> str:
    base_url = get_config_value("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL).strip().rstrip("/")
    return f"{base_url}/chat/completions"


def select_pronunciation(word: str, preferred_ipa: str | None = None) -> str:
    phones = pronouncing.phones_for_word(word) if pronouncing is not None else []
    if not phones:
        return ""

    preferred = normalize_ipa(preferred_ipa)
    if preferred:
        return min(
            phones,
            key=lambda item: ipa_distance(normalize_ipa(phones_to_ipa(item.split())), preferred),
        )
    return phones[0]


def preferred_display_ipa(value: str | None) -> str:
    if not value:
        return ""
    text = value.strip()
    if not text:
        return ""
    return text if text.startswith("/") and text.endswith("/") else f"/{text.strip('/')}/"


def pronunciation_segments(word: str) -> list[str]:
    manual = MANUAL_SEGMENTS.get(word)
    if manual is not None:
        return manual

    compound_parts = split_compound(word)
    if len(compound_parts) > 1 and "".join(compound_parts) == word:
        return compound_parts

    return [word]


def split_compound(word: str) -> list[str]:
    if wordninja is None or len(word) < MIN_COMPOUND_LENGTH:
        return []
    parts = [part for part in wordninja.split(word) if part]
    if len(parts) <= 1:
        return []
    if any(len(part) <= 1 for part in parts):
        return []
    return parts


def split_phonemes_into_segments(
    phonemes: list[str],
    target_count: int,
    segment_texts: list[str] | None = None,
) -> list[list[str]]:
    if target_count <= 1:
        return [phonemes]

    vowel_indexes = [index for index, phoneme in enumerate(phonemes) if is_vowel(phoneme)]
    if len(vowel_indexes) != target_count:
        return []

    result: list[list[str]] = []
    start = 0
    for segment_index in range(target_count - 1):
        current_vowel = vowel_indexes[segment_index]
        next_vowel = vowel_indexes[segment_index + 1]
        consonant_cluster = phonemes[current_vowel + 1 : next_vowel]
        onset_count = onset_size(consonant_cluster)
        boundary = next_vowel - onset_count
        text = segment_texts[segment_index] if segment_texts and segment_index < len(segment_texts) else ""
        if text and ends_with_consonant_letter(text) and boundary <= current_vowel + 1 < next_vowel:
            boundary = current_vowel + 2
        if boundary <= start:
            boundary = current_vowel + 1
        result.append(phonemes[start:boundary])
        start = boundary
    result.append(phonemes[start:])
    return result


def onset_size(cluster: list[str]) -> int:
    if not cluster:
        return 0
    if len(cluster) == 1:
        return 1
    last_two = tuple(strip_stress(item) for item in cluster[-2:])
    if last_two in COMMON_TWO_CONSONANT_ONSETS:
        return 2
    return 1


def ends_with_consonant_letter(text: str) -> bool:
    cleaned = re.sub(r"[^a-z]", "", text.lower())
    return bool(cleaned) and cleaned[-1] not in {"a", "e", "i", "o", "u", "y"}


def is_vowel(phoneme: str) -> bool:
    return strip_stress(phoneme) in VOWELS


def strip_stress(phoneme: str) -> str:
    return phoneme.rstrip("012")


def phones_to_ipa(phonemes: list[str]) -> str:
    return "/" + "".join(
        ARPABET_TO_IPA.get(strip_stress(phoneme), strip_stress(phoneme).lower())
        for phoneme in phonemes
    ) + "/"


def normalize_ipa(value: str | None) -> str:
    if not value:
        return ""
    return (
        value.lower()
        .strip()
        .replace("/", "")
        .replace("ˈ", "")
        .replace("ˌ", "")
        .replace("ɚ", "ər")
        .replace("ɝ", "ər")
    )


def ipa_distance(left: str, right: str) -> int:
    if not left:
        return len(right)
    if not right:
        return len(left)
    previous = list(range(len(right) + 1))
    for i, left_char in enumerate(left, start=1):
        current = [i]
        for j, right_char in enumerate(right, start=1):
            current.append(
                min(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + (0 if left_char == right_char else 1),
                )
            )
        previous = current
    return previous[-1]


MIN_COMPOUND_LENGTH = 7
DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-v4-flash"
REQUEST_TIMEOUT_SECONDS = 20
MAX_MEMORY_CHUNKS = 8
MAX_CHUNK_TEXT_LENGTH = 24
MAX_SHORT_TEXT_LENGTH = 80
MAX_RULE_TEXT_LENGTH = 140
MANUAL_SEGMENTS = {
    "website": ["web", "site"],
    "westminster": ["west", "min", "ster"],
}
VOWELS = {
    "AA",
    "AE",
    "AH",
    "AO",
    "AW",
    "AY",
    "EH",
    "ER",
    "EY",
    "IH",
    "IY",
    "OW",
    "OY",
    "UH",
    "UW",
}

PHONICS_SYSTEM_PROMPT = """
你是英语学习 App 的“拼读记忆”助手。你的任务不是做词典释义，而是把一个英文单词切成适合初学者记忆的拼写块，并让每个拼写块对应一个读音块。
必须只输出 JSON 对象，不要输出 Markdown。

输出格式：
{
  "chunks": [
    {
      "text": "ad",
      "ipa": "/əd/",
      "phonemes": ["AH0", "D"],
      "spans": [
        {"text": "a", "silent": false},
        {"text": "d", "silent": false}
      ],
      "silent": false,
      "stress": "unstressed",
      "rule": "非重读前缀 ad 弱读成 /əd/"
    }
  ]
}

规则：
- chunks 必须按顺序完整拼回输入 word，不能遗漏或增加字母。
- text 只能使用原单词中的连续字母，保持小写。
- ipa 使用美式 IPA；不知道某块精确 IPA 时也要给适合记忆的近似块，但不要编造离谱读音。
- spans 用来标记 chunk 内部哪些连续字母发音、哪些不发音；spans 拼接后必须等于本 chunk 的 text。
- 如果一个 chunk 里只有部分字母不发音，比如 struggle 的 ggle，chunk.silent=false，spans 标出 {"text":"e","silent":true}。
- silent=true 只用于整个 chunk 都不发音或几乎听不出来的情况，ipa 置空。
- stress 只能是 primary, secondary, unstressed, silent, unknown 之一。
- rule 用简短中文解释这一块为什么这样读，面向初学者。
- 优先切成 2 到 5 个记忆块。不要把普通长词整词返回，除非真的无法可靠切分。
- 切块目标是帮助记忆拼写和读音的对应关系，不是严格词典音节，也不是词根分析。
- 遇到 advantage 这类词，应倾向 ad / van / tage 这种学习者友好的块。
- 遇到 dangerous 这类词，应避免 dang 造成 /dæŋ/ 误解，可用 dan / ger / ous 这类更贴近读音的块。
- 如果某个组合是例外读法，要在 rule 中说明。
""".strip()

COMMON_TWO_CONSONANT_ONSETS = {
    ("B", "L"),
    ("B", "R"),
    ("D", "R"),
    ("F", "L"),
    ("F", "R"),
    ("G", "L"),
    ("G", "R"),
    ("K", "L"),
    ("K", "R"),
    ("P", "L"),
    ("P", "R"),
    ("S", "K"),
    ("S", "L"),
    ("S", "M"),
    ("S", "N"),
    ("S", "P"),
    ("S", "T"),
    ("S", "W"),
    ("T", "R"),
    ("TH", "R"),
}
ARPABET_TO_IPA = {
    "AA": "ɑ",
    "AE": "æ",
    "AH": "ʌ",
    "AO": "ɔ",
    "AW": "aʊ",
    "AY": "aɪ",
    "B": "b",
    "CH": "tʃ",
    "D": "d",
    "DH": "ð",
    "EH": "ɛ",
    "ER": "ɝ",
    "EY": "eɪ",
    "F": "f",
    "G": "g",
    "HH": "h",
    "IH": "ɪ",
    "IY": "i",
    "JH": "dʒ",
    "K": "k",
    "L": "l",
    "M": "m",
    "N": "n",
    "NG": "ŋ",
    "OW": "oʊ",
    "OY": "ɔɪ",
    "P": "p",
    "R": "r",
    "S": "s",
    "SH": "ʃ",
    "T": "t",
    "TH": "θ",
    "UH": "ʊ",
    "UW": "u",
    "V": "v",
    "W": "w",
    "Y": "j",
    "Z": "z",
    "ZH": "ʒ",
}
