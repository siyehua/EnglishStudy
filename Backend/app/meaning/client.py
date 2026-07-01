from __future__ import annotations

import json
import urllib.error
import urllib.request

from app.config import get_config_value
from app.dictionary.client import clean_text, normalize_word
from app.schemas import WordMeaningEntry, WordMeaningResponse


class WordMeaningClient:
    def lookup(self, word: str, sentence: str = "") -> WordMeaningResponse:
        normalized = normalize_word(word)
        if not normalized:
            return not_found(word=word, normalized="", source="deepseek")

        api_key = get_config_value("DEEPSEEK_API_KEY")
        if not api_key:
            return not_found(word=word, normalized=normalized, source="deepseek:not_configured")

        payload = {
            "model": get_config_value("DEEPSEEK_MODEL", DEFAULT_MODEL),
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": build_user_prompt(normalized, sentence)},
            ],
            "thinking": {"type": "disabled"},
            "response_format": {"type": "json_object"},
            "temperature": 0.1,
            "max_tokens": 1300,
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
        except urllib.error.HTTPError as error:
            if error.code in {400, 401, 403, 404, 429}:
                return not_found(word=word, normalized=normalized, source="deepseek:error")
            raise
        except urllib.error.URLError:
            return not_found(word=word, normalized=normalized, source="deepseek:unreachable")

        content = extract_message_content(response_payload)
        parsed_meaning = parse_meaning_content(content)
        model = clean_text(payload["model"]) or DEFAULT_MODEL
        return WordMeaningResponse(
            word=word,
            normalized=normalized,
            meanings=parsed_meaning.entries,
            source=f"deepseek:{model}",
            found=bool(parsed_meaning.entries),
            sentenceChinese=parsed_meaning.sentence_chinese,
        )


def build_user_prompt(word: str, sentence: str = "") -> str:
    request = {
        "word": word,
        "sentence": sentence.strip(),
    }
    return (
        "请根据下面的 JSON 输入，返回这个英文单词按常见词性划分的中文释义、例句和原文句子中文。"
        "必须只返回 JSON。\n"
        f"{json.dumps(request, ensure_ascii=False)}"
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
    return clean_text(message.get("content"))


class ParsedMeaning:
    def __init__(self, entries: list[WordMeaningEntry], sentence_chinese: str = "") -> None:
        self.entries = entries
        self.sentence_chinese = sentence_chinese


def parse_meaning_content(content: str | None) -> ParsedMeaning:
    if not content:
        return ParsedMeaning(entries=[])
    try:
        payload = json.loads(content)
    except json.JSONDecodeError:
        return ParsedMeaning(entries=[])

    meanings_payload = extract_meanings_payload(payload)
    if meanings_payload is None:
        return ParsedMeaning(entries=[], sentence_chinese=clean_sentence_chinese(payload))

    entries: list[WordMeaningEntry] = []
    seen_parts: set[str] = set()
    for item in iter_meaning_items(meanings_payload):
        part_of_speech = normalize_part_of_speech(
            item.get("partOfSpeech")
            or item.get("part_of_speech")
            or item.get("pos")
            or item.get("label")
        )
        if part_of_speech is None or part_of_speech in seen_parts:
            continue

        meaning = clean_meaning(
            item.get("meaning")
            or item.get("definition")
            or item.get("translation")
            or item.get("chinese")
        )
        if meaning is None:
            continue
        example = clean_example(
            item.get("example")
            or item.get("exampleSentence")
            or item.get("example_sentence")
            or item.get("sentence")
        )
        example_chinese = clean_example(
            item.get("exampleChinese")
            or item.get("example_chinese")
            or item.get("exampleTranslation")
            or item.get("example_translation")
            or item.get("translationChinese")
        )

        seen_parts.add(part_of_speech)
        entries.append(
            WordMeaningEntry(
                partOfSpeech=part_of_speech,
                label=PART_OF_SPEECH_LABELS[part_of_speech],
                meaning=meaning,
                example=example or "",
                exampleChinese=example_chinese or "",
            )
        )
        if len(entries) >= MAX_MEANING_ENTRIES:
            break

    return ParsedMeaning(
        entries=entries,
        sentence_chinese=clean_sentence_chinese(payload),
    )


def extract_meanings_payload(payload: object) -> object | None:
    if isinstance(payload, dict):
        for key in ("meanings", "definitions", "items"):
            value = payload.get(key)
            if value is not None:
                return value
    return None


def iter_meaning_items(payload: object) -> list[dict[str, object]]:
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if isinstance(payload, dict):
        items: list[dict[str, object]] = []
        for key, value in payload.items():
            if isinstance(value, dict):
                item = dict(value)
                item.setdefault("partOfSpeech", key)
                items.append(item)
            else:
                items.append({"partOfSpeech": key, "meaning": value})
        return items
    return []


def normalize_part_of_speech(value: object) -> str | None:
    text = clean_text(value)
    if text is None:
        return None
    normalized = text.lower().replace("-", "_").replace(" ", "_")
    normalized = normalized.strip("：:")
    return PART_OF_SPEECH_ALIASES.get(normalized)


def clean_meaning(value: object) -> str | None:
    if isinstance(value, list):
        value = "；".join(
            item.strip()
            for item in value
            if isinstance(item, str) and item.strip()
        )
    text = clean_text(value)
    if text is None:
        return None
    return "；".join(
        part.strip(" ;；")
        for part in text.replace("\n", "；").split("；")
        if part.strip(" ;；")
    )[:MAX_MEANING_LENGTH]


def clean_example(value: object) -> str | None:
    text = clean_text(value)
    if text is None:
        return None
    return " ".join(text.split())[:MAX_EXAMPLE_LENGTH]


def clean_sentence_chinese(payload: object) -> str:
    if not isinstance(payload, dict):
        return ""
    value = (
        payload.get("sentenceChinese")
        or payload.get("sentence_chinese")
        or payload.get("sentenceTranslation")
        or payload.get("sentence_translation")
        or payload.get("sourceSentenceChinese")
    )
    return clean_example(value) or ""


def not_found(word: str, normalized: str, source: str) -> WordMeaningResponse:
    return WordMeaningResponse(
        word=word,
        normalized=normalized,
        meanings=[],
        source=source,
        found=False,
        sentenceChinese="",
    )


def chat_completion_url() -> str:
    base_url = get_config_value("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL).strip().rstrip("/")
    return f"{base_url}/chat/completions"


PART_OF_SPEECH_LABELS = {
    "noun": "名词",
    "verb": "动词",
    "adjective": "形容词",
    "adverb": "副词",
    "preposition": "介词",
    "conjunction": "连词",
    "pronoun": "代词",
    "determiner": "限定词",
    "interjection": "感叹词",
    "auxiliary": "助动词",
    "modal": "情态动词",
    "article": "冠词",
    "numeral": "数词",
}

PART_OF_SPEECH_ALIASES = {
    key: key for key in PART_OF_SPEECH_LABELS
} | {
    "n": "noun",
    "noun.": "noun",
    "名词": "noun",
    "v": "verb",
    "verb.": "verb",
    "动词": "verb",
    "adj": "adjective",
    "adj.": "adjective",
    "adjective.": "adjective",
    "形容词": "adjective",
    "adv": "adverb",
    "adv.": "adverb",
    "adverb.": "adverb",
    "副词": "adverb",
    "prep": "preposition",
    "prep.": "preposition",
    "preposition.": "preposition",
    "介词": "preposition",
    "conj": "conjunction",
    "conj.": "conjunction",
    "conjunction.": "conjunction",
    "连词": "conjunction",
    "pron": "pronoun",
    "pron.": "pronoun",
    "pronoun.": "pronoun",
    "代词": "pronoun",
    "det": "determiner",
    "det.": "determiner",
    "determiner.": "determiner",
    "限定词": "determiner",
    "int": "interjection",
    "interj": "interjection",
    "interj.": "interjection",
    "interjection.": "interjection",
    "感叹词": "interjection",
    "aux": "auxiliary",
    "aux.": "auxiliary",
    "auxiliary_verb": "auxiliary",
    "助动词": "auxiliary",
    "modal_verb": "modal",
    "modal": "modal",
    "情态动词": "modal",
    "art": "article",
    "article.": "article",
    "冠词": "article",
    "num": "numeral",
    "number": "numeral",
    "numeral.": "numeral",
    "数词": "numeral",
}

SYSTEM_PROMPT = """
你是英语学习 App 的英汉词典助手。你的任务是把一个英文单词按常见词性整理成中文释义。

要求：
- 必须只输出 JSON 对象，不要输出 Markdown 或解释文字。
- JSON 顶层必须包含 meanings 数组。
- meanings 中每项必须包含 partOfSpeech、label、meaning、example、exampleChinese。
- 顶层必须包含 sentenceChinese；如果输入 sentence 为空，sentenceChinese 返回空字符串。
- partOfSpeech 只能使用以下英文值：noun, verb, adjective, adverb, preposition, conjunction, pronoun, determiner, interjection, auxiliary, modal, article, numeral。
- label 使用对应中文词性：名词、动词、形容词、副词、介词、连词、代词、限定词、感叹词、助动词、情态动词、冠词、数词。
- meaning 使用简洁中文，用分号分隔同一词性下的常见义项。
- example 必须是自然、短小的英文例句，并且必须使用输入 word 的同一词形，不要偷偷改成原形。例如输入 advertising，名词例句和动词例句都要使用 advertising。
- exampleChinese 是 example 的自然中文翻译。
- sentenceChinese 是输入 sentence 的中文翻译；如果 sentence 不为空必须翻译整句，不要只翻译被点中的单词。
- 只返回这个单词常见、现代的词性和释义；没有该词性就不要返回，不能编造。
- 如果输入不是可解释的英文单词，返回 {"meanings": [], "sentenceChinese": ""}。
- sentence 只用于判断常见语境优先级，不要只返回句中一个意思。

示例 JSON 输出：
{
  "meanings": [
    {
      "partOfSpeech": "noun",
      "label": "名词",
      "meaning": "跑步；一段连续的事情；经营期",
      "example": "Running every morning keeps me healthy.",
      "exampleChinese": "每天早上跑步让我保持健康。"
    },
    {
      "partOfSpeech": "verb",
      "label": "动词",
      "meaning": "跑；经营；运行；延续",
      "example": "They are running a small cafe downtown.",
      "exampleChinese": "他们正在市中心经营一家小咖啡馆。"
    }
  ],
  "sentenceChinese": "她经营一家小商店。"
}
""".strip()

DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-v4-flash"
REQUEST_TIMEOUT_SECONDS = 20
MAX_MEANING_ENTRIES = 12
MAX_MEANING_LENGTH = 180
MAX_EXAMPLE_LENGTH = 220
