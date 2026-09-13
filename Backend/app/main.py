import logging
import os
import subprocess
import tempfile
import urllib.request
from pathlib import Path

from fastapi import FastAPI, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response as RawResponse

from app.content.client import ContentClient, englishpod_original_audio_url
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
phonics_client = WordPhonicsClient()
logger = logging.getLogger("uvicorn.error")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


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


if __name__ == "__main__":
    import uvicorn

    reload = os.getenv("ENV", "prod") == "dev"
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=reload)
