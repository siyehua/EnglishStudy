from __future__ import annotations

import json
import urllib.error
import urllib.request

from app.config import get_config_value
from app.dictionary.client import clean_text, normalize_word
from app.schemas import WordFormResponse, WordFormVariant, WordFormVariantGroup, WordRelation


class WordFormEnricher:
    def enrich(self, word_form: WordFormResponse, sentence: str = "") -> WordFormResponse:
        fixed_variants = fixed_variants_for(word_form.headword)
        if fixed_variants:
            return word_form.model_copy(
                update={
                    "currentPartOfSpeech": "verb",
                    "currentPartOfSpeechLabel": "动词",
                    "variantGroups": [
                        WordFormVariantGroup(
                            partOfSpeech="verb",
                            label="动词",
                            isCurrent=True,
                            variants=fixed_variants,
                        )
                    ],
                    "source": append_source(word_form.source, "fixed_forms"),
                }
            )

        llm_result = lookup_word_form(
            surface=word_form.normalized,
            sentence=sentence,
        )
        if llm_result is None:
            return word_form.model_copy(
                update={
                    "variantGroups": fallback_variant_groups(word_form),
                }
            )

        return merge_llm_result(word_form, llm_result)


def lookup_word_form(surface: str, sentence: str = "") -> LlmWordFormResult | None:
    normalized = normalize_word(surface)
    if not normalized:
        return None

    api_key = get_config_value("DEEPSEEK_API_KEY")
    if not api_key:
        return None

    model = get_config_value("DEEPSEEK_MODEL", DEFAULT_MODEL)
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": json.dumps(
                    {
                        "surface": normalized,
                        "sentence": sentence.strip(),
                    },
                    ensure_ascii=False,
                ),
            },
        ],
        "thinking": {"type": "disabled"},
        "response_format": {"type": "json_object"},
        "temperature": 0,
        "max_tokens": 1000,
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
    except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError):
        return None

    return parse_word_form_content(extract_message_content(response_payload))


def parse_word_form_content(content: str | None) -> LlmWordFormResult | None:
    if not content:
        return None
    try:
        payload = json.loads(content)
    except json.JSONDecodeError:
        return None

    if not isinstance(payload, dict):
        return None

    headword = normalize_word(clean_text(payload.get("headword")) or "")
    relation = parse_relation(payload.get("relation"), headword)
    groups = parse_variant_groups(payload.get("variantGroups"))
    current_part_of_speech = clean_part_of_speech(payload.get("currentPartOfSpeech"))
    current_part_of_speech_label = clean_text(payload.get("currentPartOfSpeechLabel")) or ""
    if not headword and not relation and not groups:
        return None

    return LlmWordFormResult(
        headword=headword or None,
        relation=relation,
        variant_groups=groups,
        current_part_of_speech=current_part_of_speech,
        current_part_of_speech_label=current_part_of_speech_label,
    )


def parse_relation(value: object, headword: str) -> WordRelation | None:
    if not isinstance(value, dict):
        return None

    relation_type = clean_relation_type(value.get("type"))
    if relation_type is None:
        return None

    target = normalize_word(clean_text(value.get("target")) or "") or headword
    if not target:
        return None

    label = clean_text(value.get("label")) or default_relation_label(relation_type, target)
    return WordRelation(
        type=relation_type,
        target=target,
        label=label[:MAX_LABEL_LENGTH],
    )


def parse_variants(value: object) -> list[WordFormVariant]:
    items = value if isinstance(value, list) else []
    variants: list[WordFormVariant] = []
    seen: set[tuple[str, str]] = set()

    for item in items:
        if not isinstance(item, dict):
            continue
        variant_type = clean_variant_type(item.get("type"))
        label = clean_text(item.get("label"))
        variant_value = clean_variant_value(item.get("value"))
        if variant_type is None or label is None or variant_value is None:
            continue
        dedupe_key = (variant_type, variant_value.casefold())
        if dedupe_key in seen:
            continue
        seen.add(dedupe_key)
        variants.append(
            WordFormVariant(
                type=variant_type,
                label=label[:MAX_LABEL_LENGTH],
                value=variant_value,
            )
        )
        if len(variants) >= MAX_VARIANTS:
            break

    return variants


def parse_variant_groups(value: object) -> list[WordFormVariantGroup]:
    items = value if isinstance(value, list) else []
    groups: list[WordFormVariantGroup] = []
    seen_parts: set[str] = set()

    for item in items:
        if not isinstance(item, dict):
            continue
        part_of_speech = clean_part_of_speech(item.get("partOfSpeech"))
        label = clean_text(item.get("label"))
        variants = parse_variants(item.get("variants"))
        if part_of_speech is None or label is None or not variants:
            continue
        if part_of_speech in seen_parts:
            continue
        seen_parts.add(part_of_speech)
        groups.append(
            WordFormVariantGroup(
                partOfSpeech=part_of_speech,
                label=label[:MAX_LABEL_LENGTH],
                isCurrent=bool(item.get("isCurrent")),
                variants=variants,
            )
        )
        if len(groups) >= MAX_GROUPS:
            break

    if groups and not any(group.isCurrent for group in groups):
        groups[0].isCurrent = True
    return groups


def merge_llm_result(
    word_form: WordFormResponse,
    result: LlmWordFormResult,
) -> WordFormResponse:
    headword = result.headword or word_form.headword
    current_group = next((group for group in result.variant_groups if group.isCurrent), None)
    current_part_of_speech = result.current_part_of_speech or current_group.partOfSpeech if current_group else result.current_part_of_speech
    current_part_of_speech_label = (
        result.current_part_of_speech_label
        or current_group.label if current_group else result.current_part_of_speech_label
    )
    relation = result.relation
    if relation is None and headword != word_form.normalized:
        relation = WordRelation(
            type="derived_or_inflected_form_of",
            target=headword,
            label=f"form of {headword}",
        )

    return word_form.model_copy(
        update={
            "headword": headword,
            "relation": relation,
            "currentPartOfSpeech": current_part_of_speech,
            "currentPartOfSpeechLabel": current_part_of_speech_label,
            "variantGroups": result.variant_groups or fallback_variant_groups(
                word_form.model_copy(
                    update={
                        "headword": headword,
                        "relation": relation,
                        "currentPartOfSpeech": current_part_of_speech,
                        "currentPartOfSpeechLabel": current_part_of_speech_label,
                    }
                )
            ),
            "confidence": "medium" if headword != word_form.normalized else word_form.confidence,
            "source": append_source(word_form.source, "deepseek_forms"),
        }
    )


def fallback_variants(word_form: WordFormResponse) -> list[WordFormVariant]:
    variants = [
        WordFormVariant(type="base", label="原形", value=word_form.headword),
    ]
    if word_form.expansion:
        variants.append(
            WordFormVariant(type="expansion", label="完整形式", value=word_form.expansion)
        )
    if word_form.relation is not None and word_form.normalized != word_form.headword:
        variants.append(
            WordFormVariant(
                type=word_form.relation.type,
                label=relation_type_label(word_form.relation.type),
                value=word_form.normalized,
            )
        )
    return variants


def fallback_variant_groups(word_form: WordFormResponse) -> list[WordFormVariantGroup]:
    return [
        WordFormVariantGroup(
            partOfSpeech=word_form.currentPartOfSpeech or "unknown",
            label=word_form.currentPartOfSpeechLabel or "词形",
            isCurrent=True,
            variants=fallback_variants(word_form),
        )
    ]


def fixed_variants_for(headword: str) -> list[WordFormVariant]:
    return FIXED_VARIANTS.get(normalize_word(headword), [])


def relation_type_label(relation_type: str) -> str:
    return {
        "plural_of": "复数",
        "singular_of": "单数",
        "past_tense_of": "过去式",
        "past_participle_of": "过去分词",
        "present_participle_of": "现在分词",
        "third_person_singular_of": "第三人称单数",
        "first_person_singular_of": "第一人称单数",
        "present_plural_or_second_person_of": "现在时复数/第二人称",
        "past_tense_singular_of": "过去式单数",
        "past_tense_plural_of": "过去式复数",
        "contraction_of": "缩写",
        "past_or_base_form_of": "过去式/原形",
        "comparative_of": "比较级",
        "superlative_of": "最高级",
        "derived_or_inflected_form_of": "词形",
    }.get(relation_type, "词形")


def default_relation_label(relation_type: str, target: str) -> str:
    return f"{relation_type_label(relation_type)} {target}"


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


def clean_relation_type(value: object) -> str | None:
    text = clean_text(value)
    if text is None:
        return None
    normalized = text.lower().replace("-", "_").replace(" ", "_")
    return normalized if normalized in ALLOWED_RELATION_TYPES else None


def clean_variant_type(value: object) -> str | None:
    text = clean_text(value)
    if text is None:
        return None
    normalized = text.lower().replace("-", "_").replace(" ", "_")
    return normalized if normalized in ALLOWED_VARIANT_TYPES else None


def clean_part_of_speech(value: object) -> str | None:
    text = clean_text(value)
    if text is None:
        return None
    normalized = text.lower().replace("-", "_").replace(" ", "_")
    return normalized if normalized in ALLOWED_PARTS_OF_SPEECH else None


def clean_variant_value(value: object) -> str | None:
    text = clean_text(value)
    if text is None:
        return None
    cleaned = " / ".join(
        part.strip()
        for part in text.replace(",", "/").split("/")
        if part.strip()
    )
    return cleaned[:MAX_VALUE_LENGTH] if cleaned else None


def append_source(source: str, addition: str) -> str:
    return source if addition in source.split("+") else f"{source}+{addition}"


def chat_completion_url() -> str:
    base_url = get_config_value("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL).strip().rstrip("/")
    return f"{base_url}/chat/completions"


class LlmWordFormResult:
    def __init__(
        self,
        headword: str | None,
        relation: WordRelation | None,
        variant_groups: list[WordFormVariantGroup],
        current_part_of_speech: str | None,
        current_part_of_speech_label: str,
    ) -> None:
        self.headword = headword
        self.relation = relation
        self.variant_groups = variant_groups
        self.current_part_of_speech = current_part_of_speech
        self.current_part_of_speech_label = current_part_of_speech_label


DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-v4-flash"
REQUEST_TIMEOUT_SECONDS = 20
MAX_VARIANTS = 10
MAX_GROUPS = 4
MAX_LABEL_LENGTH = 24
MAX_VALUE_LENGTH = 80
ALLOWED_PARTS_OF_SPEECH = {
    "noun",
    "verb",
    "adjective",
    "adverb",
    "pronoun",
    "preposition",
    "conjunction",
    "determiner",
    "interjection",
    "unknown",
}
ALLOWED_RELATION_TYPES = {
    "plural_of",
    "singular_of",
    "past_tense_of",
    "past_participle_of",
    "present_participle_of",
    "third_person_singular_of",
    "first_person_singular_of",
    "present_plural_or_second_person_of",
    "past_tense_singular_of",
    "past_tense_plural_of",
    "contraction_of",
    "past_or_base_form_of",
    "comparative_of",
    "superlative_of",
    "derived_or_inflected_form_of",
}
ALLOWED_VARIANT_TYPES = {
    "base",
    "plural",
    "singular",
    "first_person_singular",
    "present_plural",
    "third_person_singular",
    "past_singular",
    "past_plural",
    "past_tense",
    "past_participle",
    "present_participle",
    "future",
    "comparative",
    "superlative",
    "expansion",
}
FIXED_VARIANTS = {
    "be": [
        WordFormVariant(type="base", label="原形", value="be"),
        WordFormVariant(type="first_person_singular", label="第一人称单数", value="am"),
        WordFormVariant(type="third_person_singular", label="第三人称单数", value="is"),
        WordFormVariant(type="present_plural", label="现在时复数/第二人称", value="are"),
        WordFormVariant(type="past_singular", label="过去式单数", value="was"),
        WordFormVariant(type="past_plural", label="过去式复数", value="were"),
        WordFormVariant(type="past_participle", label="过去分词", value="been"),
        WordFormVariant(type="present_participle", label="现在分词", value="being"),
        WordFormVariant(type="future", label="将来时", value="will be"),
    ],
    "do": [
        WordFormVariant(type="base", label="原形", value="do"),
        WordFormVariant(type="third_person_singular", label="第三人称单数", value="does"),
        WordFormVariant(type="past_tense", label="过去式", value="did"),
        WordFormVariant(type="past_participle", label="过去分词", value="done"),
        WordFormVariant(type="present_participle", label="现在分词", value="doing"),
        WordFormVariant(type="future", label="将来时", value="will do"),
    ],
    "have": [
        WordFormVariant(type="base", label="原形", value="have"),
        WordFormVariant(type="third_person_singular", label="第三人称单数", value="has"),
        WordFormVariant(type="past_tense", label="过去式", value="had"),
        WordFormVariant(type="past_participle", label="过去分词", value="had"),
        WordFormVariant(type="present_participle", label="现在分词", value="having"),
        WordFormVariant(type="future", label="将来时", value="will have"),
    ],
}
SYSTEM_PROMPT = """
You are an English morphology assistant for a study app.
Return only a JSON object with headword, currentPartOfSpeech, currentPartOfSpeechLabel, relation, and variantGroups.
Use Chinese labels for currentPartOfSpeechLabel, relation.label, group.label, and variant.label.

Input:
- surface: the exact clicked word, already lowercased.
- sentence: optional context.

Rules:
- First judge the part of speech of the surface in the given sentence.
- currentPartOfSpeech must describe the surface in this sentence, not every possible dictionary use.
- If the word has multiple common parts of speech, return separate variantGroups for each useful part of speech.
- Mark exactly one group isCurrent=true: the group matching the surface in this sentence.
- Put the current group first.
- Identify the real dictionary headword/base form. Do not guess by simple suffix stripping.
- If the surface is already the natural headword, set headword to surface and relation to null.
- If the surface is an inflected form, set headword to its base and relation to the specific relationship.
- For verbs, include: base, third_person_singular, past_tense, past_participle, present_participle, future.
- For regular future, use "will + base", for example "will protect".
- For countable nouns, include: singular and plural.
- For adjectives/adverbs with common grading, include: base, comparative, superlative.
- Do not invent noun plurals for adjectives such as "famous" or "serious".
- Do not include a type if the form is not natural or not applicable.
- For words with multiple common parts of speech, keep each part of speech in its own group.
- relation.type must be one of: plural_of, singular_of, past_tense_of, past_participle_of, present_participle_of, third_person_singular_of, comparative_of, superlative_of, derived_or_inflected_form_of.
- variant.type must be one of: base, plural, singular, third_person_singular, past_tense, past_participle, present_participle, future, comparative, superlative, expansion.
- group.partOfSpeech and currentPartOfSpeech must be one of: noun, verb, adjective, adverb, pronoun, preposition, conjunction, determiner, interjection, unknown.

Example for surface "protected":
{"headword":"protect","currentPartOfSpeech":"verb","currentPartOfSpeechLabel":"动词","relation":{"type":"past_tense_of","target":"protect","label":"protect 的过去式"},"variantGroups":[
  {"partOfSpeech":"verb","label":"动词","isCurrent":true,"variants":[
    {"type":"base","label":"原形","value":"protect"},
    {"type":"third_person_singular","label":"第三人称单数","value":"protects"},
    {"type":"past_tense","label":"过去式","value":"protected"},
    {"type":"past_participle","label":"过去分词","value":"protected"},
    {"type":"present_participle","label":"现在分词","value":"protecting"},
    {"type":"future","label":"将来时","value":"will protect"}
  ]}
]}

Example for surface "famous":
{"headword":"famous","currentPartOfSpeech":"adjective","currentPartOfSpeechLabel":"形容词","relation":null,"variantGroups":[
  {"partOfSpeech":"adjective","label":"形容词","isCurrent":true,"variants":[
    {"type":"base","label":"原形","value":"famous"},
    {"type":"comparative","label":"比较级","value":"more famous"},
    {"type":"superlative","label":"最高级","value":"most famous"}
  ]}
]}

Example for surface "shows" in sentence "The new season has two shows.":
{"headword":"show","currentPartOfSpeech":"noun","currentPartOfSpeechLabel":"名词","relation":{"type":"plural_of","target":"show","label":"show 的复数"},"variantGroups":[
  {"partOfSpeech":"noun","label":"名词","isCurrent":true,"variants":[
    {"type":"singular","label":"单数","value":"show"},
    {"type":"plural","label":"复数","value":"shows"}
  ]},
  {"partOfSpeech":"verb","label":"动词","isCurrent":false,"variants":[
    {"type":"base","label":"原形","value":"show"},
    {"type":"third_person_singular","label":"第三人称单数","value":"shows"},
    {"type":"past_tense","label":"过去式","value":"showed"},
    {"type":"past_participle","label":"过去分词","value":"shown / showed"},
    {"type":"present_participle","label":"现在分词","value":"showing"},
    {"type":"future","label":"将来时","value":"will show"}
  ]}
]}
""".strip()
