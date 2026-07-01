from __future__ import annotations

import os
from pathlib import Path
from typing import Iterable


CONFIG_FILE = Path(__file__).resolve().parents[1] / ".env"


def get_config_value(name: str, default: str = "") -> str:
    env_value = os.environ.get(name)
    if env_value is not None and env_value.strip():
        return env_value.strip()

    file_value = load_local_env().get(name, "").strip()
    return file_value or default


def load_local_env(path: Path = CONFIG_FILE) -> dict[str, str]:
    try:
        return parse_env_lines(path.read_text(encoding="utf-8").splitlines())
    except OSError:
        return {}


def parse_env_lines(lines: Iterable[str]) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in lines:
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export ") :].lstrip()

        key, separator, value = line.partition("=")
        if not separator:
            continue

        key = key.strip()
        if not key:
            continue

        values[key] = unquote_value(value.strip())

    return values


def unquote_value(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value
