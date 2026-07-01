import logging

from fastapi import FastAPI, HTTPException, Response

from app.content.client import ContentClient
from app.dictionary.client import DictionaryClient
from app.meaning.client import WordMeaningClient
from app.schemas import (
    ContentFetchRequest,
    ContentFetchResponse,
    TtsAudioRequest,
    WordFormRequest,
    WordFormResponse,
    WordMeaningRequest,
    WordMeaningResponse,
    WordPhonicsRequest,
    WordPhonicsResponse,
    WordPronunciationRequest,
    WordPronunciationResponse,
)
from app.phonics.client import WordPhonicsClient
from app.tts.client import TtsClient, TtsConfigurationError, TtsProviderError
from app.word_forms.enricher import WordFormEnricher
from app.word_forms.resolver import WordFormResolver

app = FastAPI(title="English Study Backend")
resolver = WordFormResolver()
form_enricher = WordFormEnricher()
dictionary_client = DictionaryClient()
meaning_client = WordMeaningClient()
tts_client = TtsClient()
content_client = ContentClient()
phonics_client = WordPhonicsClient()
logger = logging.getLogger("uvicorn.error")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/word-form", response_model=WordFormResponse)
def resolve_word_form(request: WordFormRequest) -> WordFormResponse:
    word_form = resolver.resolve(surface=request.surface, sentence=request.sentence)
    return form_enricher.enrich(word_form=word_form, sentence=request.sentence)


@app.post("/word-pronunciation", response_model=WordPronunciationResponse)
def resolve_word_pronunciation(request: WordPronunciationRequest) -> WordPronunciationResponse:
    return dictionary_client.lookup(request.word)


@app.post("/word-phonics", response_model=WordPhonicsResponse)
def resolve_word_phonics(request: WordPhonicsRequest) -> WordPhonicsResponse:
    return phonics_client.lookup(
        word=request.word,
        sentence=request.sentence,
        ipa=request.ipa,
    )


@app.post("/word-meaning", response_model=WordMeaningResponse)
def resolve_word_meaning(request: WordMeaningRequest) -> WordMeaningResponse:
    return meaning_client.lookup(word=request.word, sentence=request.sentence)


@app.post("/tts-audio")
def synthesize_tts_audio(request: TtsAudioRequest) -> Response:
    logger.info("TTS request length=%s text=%r", len(request.text), request.text[:300])
    try:
        audio = tts_client.synthesize(text=request.text, instruction=request.instruction)
    except TtsConfigurationError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except TtsProviderError as error:
        raise HTTPException(status_code=502, detail=str(error)) from error

    return Response(content=audio, media_type="audio/wav")


@app.post("/contents", response_model=ContentFetchResponse)
def fetch_contents(request: ContentFetchRequest) -> ContentFetchResponse:
    items, filters = content_client.fetch(
        fetch_more=request.fetchMore,
        types=request.types,
        levels=request.levels,
        sources=request.sources,
    )
    return ContentFetchResponse(items=items, filters=filters)
