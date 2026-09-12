from __future__ import annotations

from pydantic import BaseModel, Field


class WordFormRequest(BaseModel):
    surface: str = Field(..., min_length=1)
    sentence: str = ""


class WordRelation(BaseModel):
    type: str
    target: str
    label: str


class WordFormVariant(BaseModel):
    type: str
    label: str
    value: str


class WordFormVariantGroup(BaseModel):
    partOfSpeech: str
    label: str
    isCurrent: bool = False
    variants: list[WordFormVariant] = Field(default_factory=list)


class WordFormResponse(BaseModel):
    surface: str
    normalized: str
    headword: str
    pronunciationTarget: str
    relation: WordRelation | None = None
    expansion: str | None = None
    currentPartOfSpeech: str | None = None
    currentPartOfSpeechLabel: str = ""
    variantGroups: list[WordFormVariantGroup] = Field(default_factory=list)
    confidence: str
    source: str


class WordPronunciationRequest(BaseModel):
    word: str = Field(..., min_length=1)


class WordPronunciationResponse(BaseModel):
    word: str
    normalized: str
    phonetic: str | None = None
    audioUrl: str | None = None
    source: str
    found: bool


class WordPhonicsRequest(BaseModel):
    word: str = Field(..., min_length=1)
    sentence: str = ""
    ipa: str | None = None


class WordPhonicsSegment(BaseModel):
    text: str
    phonemes: list[str] = Field(default_factory=list)
    ipa: str = ""


class WordPhonicsSpan(BaseModel):
    text: str
    silent: bool = False


class WordPhonicsChunk(BaseModel):
    text: str
    ipa: str = ""
    phonemes: list[str] = Field(default_factory=list)
    spans: list[WordPhonicsSpan] = Field(default_factory=list)
    silent: bool = False
    stress: str = ""
    rule: str = ""


class WordPhonicsResponse(BaseModel):
    word: str
    normalized: str
    ipa: str | None = None
    phonemes: list[str] = Field(default_factory=list)
    segments: list[WordPhonicsSegment] = Field(default_factory=list)
    chunks: list[WordPhonicsChunk] = Field(default_factory=list)
    note: str = ""
    verification: str = ""
    source: str
    found: bool
    message: str = ""


class WordMeaningRequest(BaseModel):
    word: str = Field(..., min_length=1)
    sentence: str = ""


class WordMeaningEntry(BaseModel):
    partOfSpeech: str
    label: str
    meaning: str
    example: str = ""
    exampleChinese: str = ""


class WordMeaningResponse(BaseModel):
    word: str
    normalized: str
    meanings: list[WordMeaningEntry]
    source: str
    found: bool
    sentenceChinese: str = ""


class TtsAudioRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=5000)
    instruction: str = Field(..., min_length=1, max_length=500)


class ContentFetchRequest(BaseModel):
    fetchMore: bool = False
    types: list[str] = []
    levels: list[str] = []
    sources: list[str] = []


class DialogueLineResponse(BaseModel):
    speaker: str
    text: str
    trans: str = ""
    section: str = ""
    start: float = 0.0
    end: float = 0.0


class ContentSectionResponse(BaseModel):
    name: str
    start: float
    end: float


class ContentItemResponse(BaseModel):
    id: str
    title: str
    type: str
    level: str
    body: str
    author: str | None = None
    source: str | None = None
    date: str = ""
    lines: list[DialogueLineResponse] = []
    audioUrl: str | None = None
    dialogueAudioUrl: str | None = None
    sections: list[ContentSectionResponse] = []


class ContentFilterOptionResponse(BaseModel):
    id: str
    label: str
    count: int


class ContentFiltersResponse(BaseModel):
    types: list[ContentFilterOptionResponse] = []
    levels: list[ContentFilterOptionResponse] = []
    sources: list[ContentFilterOptionResponse] = []


class ContentFetchResponse(BaseModel):
    items: list[ContentItemResponse]
    filters: ContentFiltersResponse | None = None
