from __future__ import annotations

import base64
import json
import urllib.error
import urllib.request

from app.config import get_config_value
from app.dictionary.client import clean_text


class TtsConfigurationError(Exception):
    pass


class TtsProviderError(Exception):
    pass


class TtsClient:
    def synthesize(self, text: str, instruction: str) -> bytes:
        clean_input = clean_text(text)
        clean_instruction = clean_text(instruction)
        if not clean_input:
            raise TtsProviderError("No text was provided for TTS.")
        if not clean_instruction:
            raise TtsProviderError("No TTS instruction was provided.")

        api_key = get_config_value("MIMO_API_KEY")
        if not api_key:
            raise TtsConfigurationError("MIMO_API_KEY is not configured.")

        payload = {
            "model": get_config_value("MIMO_MODEL", DEFAULT_MODEL),
            "audio": {
                "voice": get_config_value("MIMO_VOICE", DEFAULT_VOICE),
                "format": get_config_value("MIMO_AUDIO_FORMAT", DEFAULT_AUDIO_FORMAT),
            },
            "messages": [
                {"role": "user", "content": clean_instruction},
                {"role": "assistant", "content": clean_input},
            ],
        }
        request = urllib.request.Request(
            url=chat_completion_url(),
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "User-Agent": "EnglishStudyBackend/1.0",
                "api-key": api_key,
            },
            method="POST",
        )

        try:
            with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                response_payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            raise TtsProviderError(f"MIMO TTS request failed: HTTP {error.code}") from error
        except urllib.error.URLError as error:
            raise TtsProviderError("MIMO TTS service is unreachable.") from error
        except json.JSONDecodeError as error:
            raise TtsProviderError("MIMO TTS response was not valid JSON.") from error

        audio_data = extract_audio_data(response_payload)
        if not audio_data:
            raise TtsProviderError("MIMO TTS response did not include audio data.")

        try:
            return base64.b64decode(audio_data)
        except ValueError as error:
            raise TtsProviderError("MIMO TTS audio data was not valid base64.") from error


def extract_audio_data(payload: object) -> str | None:
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
    audio = message.get("audio")
    if not isinstance(audio, dict):
        return None
    return clean_text(audio.get("data"))


def chat_completion_url() -> str:
    base_url = get_config_value("MIMO_BASE_URL", DEFAULT_BASE_URL).strip().rstrip("/")
    return f"{base_url}/chat/completions"


DEFAULT_BASE_URL = "https://api.xiaomimimo.com/v1"
DEFAULT_MODEL = "mimo-v2.5-tts"
DEFAULT_VOICE = "Chloe"
DEFAULT_AUDIO_FORMAT = "wav"
REQUEST_TIMEOUT_SECONDS = 60
