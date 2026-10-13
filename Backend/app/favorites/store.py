"""收藏的服务端存储（单用户，不区分账号）。

收藏不像缓存那样可以丢弃，所以单独用一个 SQLite 文件（默认 data/favorites.db），
不和 cache.db 混在一起。

同步策略：
- 每条收藏用客户端生成的 ``sync_id`` 作为主键；
- 合并时按 ``updatedAt`` 做「最后写入者优先」；
- 删除写墓碑（``deleted = 1``），这样删除也能同步到别的设备。
"""

from __future__ import annotations

import os
import sqlite3
import threading
from pathlib import Path
from typing import Any

DB_PATH = Path(
    os.environ.get(
        "FAVORITES_DB",
        Path(__file__).resolve().parents[1] / "data" / "favorites.db",
    )
)

_lock = threading.Lock()

_SCHEMA = """
CREATE TABLE IF NOT EXISTS favorite (
  sync_id      TEXT PRIMARY KEY,
  bucket       TEXT NOT NULL DEFAULT 'default',
  kind         TEXT NOT NULL,
  text         TEXT NOT NULL,
  audio_url    TEXT,
  content_id   TEXT NOT NULL DEFAULT '',
  translation  TEXT NOT NULL DEFAULT '',
  lesson_title TEXT NOT NULL DEFAULT '',
  start_time   REAL NOT NULL DEFAULT 0,
  end_time     REAL NOT NULL DEFAULT 0,
  updated_at   INTEGER NOT NULL,
  deleted      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS index_favorite_bucket ON favorite(bucket);
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


def normalize_bucket(bucket: str | None) -> str:
    return (bucket or "").strip() or "default"


def _row_to_item(row: sqlite3.Row) -> dict[str, Any]:
    return {
        "syncId": row["sync_id"],
        "kind": row["kind"],
        "text": row["text"],
        "audioUrl": row["audio_url"],
        "contentId": row["content_id"],
        "translation": row["translation"],
        "lessonTitle": row["lesson_title"],
        "startTime": row["start_time"],
        "endTime": row["end_time"],
        "updatedAt": row["updated_at"],
        "deleted": bool(row["deleted"]),
    }


def _upsert(conn: sqlite3.Connection, bucket: str, item: dict[str, Any]) -> None:
    sync_id = str(item.get("syncId") or "").strip()
    if not sync_id:
        return
    conn.execute(
        """
        INSERT INTO favorite
          (sync_id, bucket, kind, text, audio_url, content_id, translation,
           lesson_title, start_time, end_time, updated_at, deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(sync_id) DO UPDATE SET
          bucket=excluded.bucket,
          kind=excluded.kind,
          text=excluded.text,
          audio_url=excluded.audio_url,
          content_id=excluded.content_id,
          translation=excluded.translation,
          lesson_title=excluded.lesson_title,
          start_time=excluded.start_time,
          end_time=excluded.end_time,
          updated_at=excluded.updated_at,
          deleted=excluded.deleted
        WHERE excluded.updated_at >= favorite.updated_at
        """,
        (
            sync_id,
            bucket,
            str(item.get("kind") or "word"),
            str(item.get("text") or ""),
            item.get("audioUrl"),
            str(item.get("contentId") or ""),
            str(item.get("translation") or ""),
            str(item.get("lessonTitle") or ""),
            float(item.get("startTime") or 0.0),
            float(item.get("endTime") or 0.0),
            int(item.get("updatedAt") or 0),
            1 if item.get("deleted") else 0,
        ),
    )


def merge(bucket: str | None, items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """合并客户端上报的收藏，返回该 bucket 的全量（含墓碑）。"""
    name = normalize_bucket(bucket)
    with _lock, _connect() as conn:
        for item in items:
            _upsert(conn, name, item)
        rows = conn.execute(
            "SELECT * FROM favorite WHERE bucket = ?", (name,)
        ).fetchall()
    return [_row_to_item(row) for row in rows]


def list_items(bucket: str | None) -> list[dict[str, Any]]:
    name = normalize_bucket(bucket)
    with _connect() as conn:
        rows = conn.execute(
            "SELECT * FROM favorite WHERE bucket = ?", (name,)
        ).fetchall()
    return [_row_to_item(row) for row in rows]


def count(bucket: str | None) -> int:
    name = normalize_bucket(bucket)
    with _connect() as conn:
        row = conn.execute(
            "SELECT COUNT(*) AS n FROM favorite WHERE bucket = ? AND deleted = 0",
            (name,),
        ).fetchone()
    return int(row["n"]) if row else 0
