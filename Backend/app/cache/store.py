"""服务端缓存：进程内 LRU + SQLite 落盘。

设计要点：
- 缓存是**可丢弃**的：清空后自动重建，不影响正确性，因此不破坏服务端的无状态性。
- key 带版本前缀，改提示词或换模型时整体失效。
- 单文件 SQLite，零运维；进程内 LRU 挡住热点请求，避免每次读盘。
"""

from __future__ import annotations

import hashlib
import json
import os
import sqlite3
import threading
import time
from collections import OrderedDict
from pathlib import Path
from typing import Any, Optional

# 改这里即可让所有旧缓存失效（提示词 / 模型 / 输出结构变化时都要改）
CACHE_VERSION = "v1"

DB_PATH = Path(
    os.environ.get("RL_CACHE_DB", Path(__file__).resolve().parents[1] / "data" / "cache.db")
)
DEFAULT_TTL_SECONDS = int(os.environ.get("RL_CACHE_TTL", str(30 * 86400)))
MAX_ENTRIES = int(os.environ.get("RL_CACHE_MAX_ENTRIES", "50000"))
LRU_CAPACITY = int(os.environ.get("RL_CACHE_LRU", "512"))

_lock = threading.Lock()
_lru: "OrderedDict[str, tuple[float, Any]]" = OrderedDict()

_SCHEMA = """
CREATE TABLE IF NOT EXISTS entry (
  cache_key   TEXT PRIMARY KEY,
  payload     TEXT NOT NULL,
  expires_at  INTEGER NOT NULL,
  created_at  INTEGER NOT NULL
);
"""


def _connect() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA busy_timeout=5000")
    return conn


def init_db() -> None:
    with _connect() as conn:
        conn.executescript(_SCHEMA)
        conn.execute("DELETE FROM entry WHERE expires_at < ?", (int(time.time()),))


def make_key(*parts: str) -> str:
    raw = "\u0001".join(("" if part is None else str(part)).strip().lower() for part in parts)
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()[:32]
    return f"{CACHE_VERSION}:{digest}"


def get(key: str) -> Optional[Any]:
    now = time.time()

    with _lock:
        hit = _lru.get(key)
        if hit is not None:
            expires_at, payload = hit
            if expires_at > now:
                _lru.move_to_end(key)
                return payload
            _lru.pop(key, None)

    with _connect() as conn:
        row = conn.execute(
            "SELECT payload, expires_at FROM entry WHERE cache_key = ?", (key,)
        ).fetchone()
        if row is None or int(row["expires_at"]) < now:
            return None

    payload = json.loads(row["payload"])
    with _lock:
        _lru[key] = (int(row["expires_at"]), payload)
        _trim_lru()
    return payload


def put(key: str, payload: Any, ttl: int = DEFAULT_TTL_SECONDS) -> None:
    expires_at = int(time.time()) + ttl
    encoded = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    now = int(time.time())

    with _lock:
        _lru[key] = (expires_at, payload)
        _trim_lru()

    with _connect() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO entry (cache_key, payload, expires_at, created_at)"
            " VALUES (?, ?, ?, ?)",
            (key, encoded, expires_at, now),
        )
        _prune(conn)


def clear() -> int:
    with _lock:
        _lru.clear()
    with _connect() as conn:
        cur = conn.execute("DELETE FROM entry")
        return cur.rowcount or 0


def stats() -> dict:
    now = int(time.time())
    with _connect() as conn:
        total = conn.execute("SELECT COUNT(*) AS n FROM entry").fetchone()["n"]
        alive = conn.execute(
            "SELECT COUNT(*) AS n FROM entry WHERE expires_at >= ?", (now,)
        ).fetchone()["n"]
    return {
        "cache_version": CACHE_VERSION,
        "cache_entries": int(total),
        "cache_alive": int(alive),
        "cache_lru": len(_lru),
    }


def _trim_lru() -> None:
    while len(_lru) > LRU_CAPACITY:
        _lru.popitem(last=False)


def _prune(conn: sqlite3.Connection) -> None:
    now = int(time.time())
    conn.execute("DELETE FROM entry WHERE expires_at < ?", (now,))
    total = conn.execute("SELECT COUNT(*) AS n FROM entry").fetchone()["n"]
    if total <= MAX_ENTRIES:
        return
    # 超出上限时按到期时间淘汰最旧的一批
    conn.execute(
        "DELETE FROM entry WHERE cache_key IN ("
        " SELECT cache_key FROM entry ORDER BY expires_at ASC LIMIT ?"
        ")",
        (total - MAX_ENTRIES,),
    )
