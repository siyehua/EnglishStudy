# English Study Backend

Lightweight backend for English Study word insight features.

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

## Run

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

## Test

```powershell
python -m unittest discover
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

## Next Providers

Keep the Android contract stable and add richer providers behind the resolver:

1. `contraction_table`: local expansion table for forms like `don't`.
2. `irregular_table`: local exception table for forms like `wrote`.
3. `suffix_rules`: conservative local rules for common plurals and verb forms.
4. `spacy_provider`: future POS-aware lemma provider.
5. `llm_fallback`: future fallback for ambiguous forms such as `he's`.
6. `cache`: persist resolved forms so repeated taps do not call external services.
