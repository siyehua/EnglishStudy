# English Study Backend

FastAPI service behind the English Study Android app: lesson content, sentence
audio cutting, word insight (form / pronunciation / phonics / meanings) and a
TTS proxy.

Project overview: [../README.md](../README.md) ·
App architecture: [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) ·
Playback: [../docs/PLAYBACK.md](../docs/PLAYBACK.md) ·
Build & release: [../docs/BUILD.md](../docs/BUILD.md)

Production: **`https://handwriter.asia/english`** (supervisor + Caddy, see
[Run On A Server](#run-on-a-server-current-production-setup)).

## Word insight

The first API resolves the clicked surface word into a structured word form record:

- `surface`: word the learner tapped
- `headword`: dictionary lookup target
- `pronunciationTarget`: word to pronounce and use for IPA
- `relation`: optional relation from the surface form to another form
- `expansion`: optional contraction expansion

The resolver is intentionally local-first. It uses built-in contraction and irregular-form tables plus conservative English suffix rules. LLM or richer NLP libraries can be added later as fallback providers.

The backend can also ask DeepSeek for concise Chinese meanings grouped by part of speech. Put your key in `Backend/.env`:

```text
DEEPSEEK_API_KEY=your_deepseek_key_here
DEEPSEEK_MODEL=deepseek-v4-flash
DEEPSEEK_BASE_URL=https://api.deepseek.com
```

System environment variables with the same names still take priority over `.env`.

TTS audio is proxied through the backend so the Android app does not ship the MIMO key:

```text
MIMO_API_KEY=your_mimo_key_here
MIMO_MODEL=mimo-v2.5-tts
MIMO_VOICE=Chloe
MIMO_AUDIO_FORMAT=wav
MIMO_BASE_URL=https://api.xiaomimimo.com/v1
```

## Run Locally

```bash
cd Backend
python3 -m venv .venv
./.venv/bin/pip install -r requirements.txt
./.venv/bin/python -m app.main            # 127.0.0.1:8000
# or, with auto-reload:
./.venv/bin/python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

## API

Base URL in production: **`https://handwriter.asia/english`**
(Caddy `handle_path /english/*` strips the prefix before proxying to
`127.0.0.1:8000`).

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | liveness probe → `{"status":"ok"}` |
| `POST` | `/contents` | lesson list (EnglishPod transcripts) |
| `GET` | `/ting/segment` | cut a sentence clip out of a lesson MP3 |
| `POST` | `/word-form` | surface word → headword / relation / expansion |
| `POST` | `/word-pronunciation` | IPA for a word |
| `POST` | `/word-phonics` | syllable / phonics breakdown |
| `POST` | `/word-meaning` | Chinese meanings + sentence translation |
| `POST` | `/tts-audio` | TTS proxy (keeps the MIMO key server-side) |

## Run On A Server (current production setup)

Server: Tencent Cloud Guangzhou (`43.139.205.128`), directory
**`/opt/EnglishStudy/Backend`**.

```bash
cd /opt/EnglishStudy/Backend
python3 -m venv .venv
./.venv/bin/pip install -r requirements.txt
mkdir -p log
```

Supervisor unit `/etc/supervisor/conf.d/english-study-api.conf`:

```ini
[program:english-study-api]
command=/opt/EnglishStudy/Backend/.venv/bin/python -m app.main
directory=/opt/EnglishStudy/Backend
autostart=true
autorestart=true
startsecs=5
startretries=3
stdout_logfile=/opt/EnglishStudy/Backend/log/stdout.log
stdout_logfile_maxbytes=10MB
stdout_logfile_backups=2
stderr_logfile=/opt/EnglishStudy/Backend/log/error.log
stderr_logfile_maxbytes=10MB
stderr_logfile_backups=2
environment=PYTHONUNBUFFERED="1"
```

Caddy route inside the `handwriter.asia` block (before the catch-all
`reverse_proxy`):

```
handle_path /english/* {
    reverse_proxy 127.0.0.1:8000
}
```

Apply changes:

```bash
supervisorctl -c /etc/supervisor/supervisord.conf reread
supervisorctl -c /etc/supervisor/supervisord.conf update english-study-api
/usr/local/bin/caddy-duckdns validate --config /etc/caddy/Caddyfile --adapter caddyfile
systemctl reload caddy
curl -s https://handwriter.asia/english/health
```

Operational commands:

```bash
S="supervisorctl -c /etc/supervisor/supervisord.conf"
$S status english-study-api
$S restart english-study-api
tail -f /opt/EnglishStudy/Backend/log/stdout.log
```

> `/usr/bin/caddy` on this host is the stock build and cannot parse the current
> Caddyfile (it needs the duckdns plugin). Always use
> `/usr/local/bin/caddy-duckdns` to validate and `systemctl reload caddy` to
> apply.

The app binds to `127.0.0.1` only; all external access goes through Caddy over
HTTPS. `Backend/.env` (with `DEEPSEEK_API_KEY` and `MIMO_API_KEY`) must exist on
the server and must not be committed.

## Content Sources

`POST /contents` returns learning content that is fetched entirely from the
EnglishPod 365 podcast transcripts (the community-maintained `bitter999/EnglishPod`
repository):

- Index: `https://raw.githubusercontent.com/bitter999/EnglishPod/main/data_index.js`
- Lessons: `https://raw.githubusercontent.com/bitter999/EnglishPod/main/data/lesson_{N}.json` (`N` = 1..365)

Each lesson becomes a single `DIALOGUE` content item holding the original
transcript segments verbatim (one segment per line). Every line carries the
English text, the transcript's own Chinese translation (`trans`, so no extra
machine translation is needed) and its timestamp, plus an `audioUrl` pointing
at `GET /ting/segment`, which cuts that segment out of the lesson audio on
demand. The level is inferred from the audio filename first and the title as a
fallback:

| Source level | Mapped level |
| --- | --- |
| Elementary | A2 |
| Intermediate | B1 |
| Upper Intermediate | B2 |
| Advanced / Advanced Media | C1 |
| Unspecified | B1 |

By default the first 20 lessons are returned; `fetchMore=true` returns the
first 100. The implementation lives in `app/content/client.py`.

### Sentence audio

`GET /ting/segment?lesson={N}&start={seconds}&end={seconds}` downloads the
lesson audio (cached under `TING_AUDIO_CACHE`), cuts the requested span with
`ffmpeg` (padded by 200 ms on both sides so quiet leading words are not lost),
and returns an MP3. Serving a pre-cut clip avoids depending on the player's
imprecise seek over the untagged VBR source MP3.

## Test

```bash
cd Backend
./.venv/bin/python -m unittest discover
```

## Example

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8000/word-form `
  -ContentType 'application/json' `
  -Body '{"surface":"wrote","sentence":"She wrote a letter."}'
```

Response:

```json
{
  "surface": "wrote",
  "normalized": "wrote",
  "headword": "write",
  "pronunciationTarget": "wrote",
  "relation": {
    "type": "past_tense_of",
    "target": "write",
    "label": "past tense of write"
  },
  "expansion": null,
  "confidence": "high",
  "source": "irregular_table"
}
```

Chinese meanings:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8000/word-meaning `
  -ContentType 'application/json' `
  -Body '{"word":"run","sentence":"She runs a small shop."}'
```

The response contains `meanings` for the tapped word and `sentenceChinese`, a
Chinese translation of the whole `sentence`. Sentence translations normally come
from the EnglishPod transcript itself; `sentenceChinese` is the fallback used
when a transcript translation is not available.

## Next Providers

Keep the Android contract stable and add richer providers behind the resolver:

1. `contraction_table`: local expansion table for forms like `don't`.
2. `irregular_table`: local exception table for forms like `wrote`.
3. `suffix_rules`: conservative local rules for common plurals and verb forms.
4. `spacy_provider`: future POS-aware lemma provider.
5. `llm_fallback`: future fallback for ambiguous forms such as `he's`.
6. `cache`: persist resolved forms so repeated taps do not call external services.
