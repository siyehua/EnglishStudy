import logging
import os
import subprocess
import tempfile
import urllib.request
from pathlib import Path

from fastapi import FastAPI, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response as RawResponse

from app.cache import store as cache_store
from app.content.client import ContentClient, englishpod_original_audio_url
from app.dictionary.client import DictionaryClient
from app.meaning.client import WordMeaningClient
from app.schemas import (
    ContentFetchRequest,
    ContentFetchResponse,
    TtsAudioRequest,
    WordFormRequest,
    WordFormResponse,
    WordInsightRequest,
    WordInsightResponse,
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

# CORS — 允许 Cloudflare 隧道跨域访问
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
resolver = WordFormResolver()
form_enricher = WordFormEnricher()
dictionary_client = DictionaryClient()
meaning_client = WordMeaningClient()
tts_client = TtsClient()
content_client = ContentClient()
cache_store.init_db()
phonics_client = WordPhonicsClient()
logger = logging.getLogger("uvicorn.error")


@app.get("/health")
def health() -> dict[str, object]:
    return {"status": "ok", **cache_store.stats()}


AUDIO_CACHE_DIR = Path(
    os.environ.get("TING_AUDIO_CACHE", "/tmp/ting_audio_cache")
)


def ensure_lesson_audio(number: int) -> Path | None:
    AUDIO_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    path = AUDIO_CACHE_DIR / f"{number}.mp3"
    if path.exists() and path.stat().st_size > 0:
        return path
    url = englishpod_original_audio_url(number)
    if not url:
        return None
    try:
        urllib.request.urlretrieve(url, path)
    except Exception:
        return None
    return path if path.exists() and path.stat().st_size > 0 else None


@app.get("/ting/segment")
def ting_segment(lesson: int, start: float, end: float) -> RawResponse:
    """Cut a single sentence out of the lesson audio so playback needs no seeking."""
    audio = ensure_lesson_audio(lesson)
    if audio is None:
        raise HTTPException(status_code=404, detail="audio unavailable")

    start = max(0.0, start)
    end = max(start + 0.05, end)

    # Expand the cut slightly so quiet leading/trailing words (e.g. "Let's")
    # are never clipped. Overlap with neighbours is preferable to losing audio.
    pad_start = max(0.0, start - 0.20)
    pad_end = end + 0.20

    handle, out_path = tempfile.mkstemp(suffix=".mp3")
    os.close(handle)
    try:
        subprocess.run(
            [
                "ffmpeg", "-y", "-v", "error",
                "-ss", f"{pad_start:.3f}",
                "-to", f"{pad_end:.3f}",
                "-i", str(audio),
                "-c:a", "libmp3lame", "-b:a", "64k",
                out_path,
            ],
            check=True,
            timeout=30,
        )
        data = Path(out_path).read_bytes()
    except Exception as error:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=str(error)) from error
    finally:
        try:
            os.unlink(out_path)
        except OSError:
            pass

    return RawResponse(
        content=data,
        media_type="audio/mpeg",
        headers={"Cache-Control": "public, max-age=86400"},
    )


@app.post("/word-form", response_model=WordFormResponse)
def resolve_word_form(request: WordFormRequest) -> WordFormResponse:
    key = cache_store.make_key("form", request.surface)
    cached = cache_store.get(key)
    if cached is not None:
        return WordFormResponse(**cached)

    word_form = resolver.resolve(surface=request.surface, sentence=request.sentence)
    enriched = form_enricher.enrich(word_form=word_form, sentence=request.sentence)
    cache_store.put(key, enriched.model_dump())
    return enriched


@app.post("/word-pronunciation", response_model=WordPronunciationResponse)
def resolve_word_pronunciation(request: WordPronunciationRequest) -> WordPronunciationResponse:
    key = cache_store.make_key("pron", request.word)
    cached = cache_store.get(key)
    if cached is not None:
        return WordPronunciationResponse(**cached)

    result = dictionary_client.lookup(request.word)
    if result.found and result.phonetic:
        cache_store.put(key, result.model_dump())
    return result


@app.post("/word-phonics", response_model=WordPhonicsResponse)
def resolve_word_phonics(request: WordPhonicsRequest) -> WordPhonicsResponse:
    key = cache_store.make_key("phonics", request.word, request.ipa or "")
    cached = cache_store.get(key)
    if cached is not None:
        return WordPhonicsResponse(**cached)

    result = phonics_client.lookup(
        word=request.word,
        sentence=request.sentence,
        ipa=request.ipa,
    )
    if result.found:
        cache_store.put(key, result.model_dump())
    return result


@app.post("/word-meaning", response_model=WordMeaningResponse)
def resolve_word_meaning(request: WordMeaningRequest) -> WordMeaningResponse:
    key = cache_store.make_key("meaning", request.word, request.sentence)
    cached = cache_store.get(key)
    if cached is not None:
        return WordMeaningResponse(**cached)

    result = meaning_client.lookup(word=request.word, sentence=request.sentence)
    if result.found:
        cache_store.put(key, result.model_dump())
    return result


@app.post("/word-insight", response_model=WordInsightResponse)
def resolve_word_insight(request: WordInsightRequest) -> WordInsightResponse:
    """聚合接口：一次返回词形 / 发音 / 读音拆分 / 释义，减少客户端往返。"""
    pronunciation = resolve_word_pronunciation(WordPronunciationRequest(word=request.word))
    word_form = resolve_word_form(
        WordFormRequest(surface=request.word, sentence=request.sentence)
    )
    phonics = resolve_word_phonics(
        WordPhonicsRequest(
            word=request.word,
            sentence=request.sentence,
            ipa=pronunciation.phonetic,
        )
    )
    meaning = resolve_word_meaning(
        WordMeaningRequest(word=request.word, sentence=request.sentence)
    )
    return WordInsightResponse(
        wordForm=word_form,
        pronunciation=pronunciation,
        phonics=phonics,
        meaning=meaning,
    )


@app.post("/admin/cache/clear")
def clear_cache() -> dict[str, int]:
    """清空缓存（缓存可丢，清空后自动重建）。"""
    return {"cleared": cache_store.clear()}


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


if __name__ == "__main__":
    import uvicorn

    reload = os.getenv("ENV", "prod") == "dev"
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=reload)
