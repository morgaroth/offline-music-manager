#!/usr/bin/env python3
"""One-time migration: pull tracks from MongoDB (mongo-latest container) and insert into PostgreSQL.

Usage:
  pip install pymongo psycopg2-binary
  python migrate_mongo_to_postgres.py

Environment variables (all optional, defaults shown):
  MONGO_URI=mongodb://localhost:27017/MusicLibrary
  MONGO_COLLECTION=Tracks
  POSTGRES_URL=postgresql://postgres@localhost:5432/music_library
"""

import os
import sys
from datetime import datetime, timezone

try:
    from pymongo import MongoClient
    import psycopg2
    from psycopg2.extras import execute_values
except ImportError:
    print("Install dependencies first: pip install pymongo psycopg2-binary")
    sys.exit(1)

MONGO_URI = os.environ.get("MONGO_URI", "mongodb://localhost:27019/MusicLibrary")
MONGO_COLLECTION = os.environ.get("MONGO_COLLECTION", "Tracks")
POSTGRES_URL = os.environ.get("POSTGRES_URL", "postgresql://postgres:homeassistant@192.168.0.20:5432/music_library")


def to_utc(value):
    """Convert a mongo datetime or None to a timezone-aware UTC datetime."""
    if value is None:
        return datetime.now(timezone.utc)
    if isinstance(value, datetime):
        if value.tzinfo is None:
            return value.replace(tzinfo=timezone.utc)
        return value
    return datetime.now(timezone.utc)


def get_status(doc):
    """Extract status string, handling both string and object representations."""
    status = doc.get("status", "draft")
    if isinstance(status, dict):
        return status.get("$type", "draft").lower()
    return str(status).lower()


def get_playlists(doc):
    """Extract playlists as a list of strings."""
    playlists = doc.get("playlists", [])
    if isinstance(playlists, set):
        return list(playlists)
    if isinstance(playlists, list):
        return playlists
    return []


def get_optional_str(doc, key):
    """Get a string field, returning None for empty strings."""
    val = doc.get(key)
    if val is None or val == "":
        return None
    return str(val)


def get_optional_int(doc, key):
    val = doc.get(key)
    if val is None:
        return None
    try:
        return int(val)
    except (ValueError, TypeError):
        return None


def get_optional_decimal(doc, key):
    val = doc.get(key)
    if val is None:
        return None
    try:
        return float(val)
    except (ValueError, TypeError):
        return None


def main():
    print(f"Connecting to MongoDB: {MONGO_URI}")
    mongo_client = MongoClient(MONGO_URI)
    db_name = MONGO_URI.rsplit("/", 1)[-1].split("?")[0]
    mongo_db = mongo_client[db_name]
    collection = mongo_db[MONGO_COLLECTION]

    docs = list(collection.find())
    print(f"Found {len(docs)} tracks in MongoDB ({MONGO_COLLECTION})")

    if not docs:
        print("Nothing to migrate.")
        return

    print(f"Connecting to PostgreSQL: {POSTGRES_URL}")
    pg_conn = psycopg2.connect(POSTGRES_URL)
    pg_conn.autocommit = False
    cur = pg_conn.cursor()

    insert_sql = """
        INSERT INTO tracks (id, url, title, artist, album, start_at, end_at,
            fade_out_seconds, volume_change, status, id_check, playlists,
            raw_title, raw_description, created_at, updated_at)
        VALUES %s
        ON CONFLICT (id) DO NOTHING
    """

    rows = []
    for doc in docs:
        track_id = str(doc["_id"])
        url = doc.get("url", "")
        title = doc.get("title", "")
        artist = doc.get("artist", "")
        album = doc.get("album", "")
        start_at = get_optional_str(doc, "startAt")
        end_at = get_optional_str(doc, "endAt")
        fade_out_seconds = get_optional_int(doc, "fadeOutSeconds")
        volume_change = get_optional_decimal(doc, "volumeChange")
        status = get_status(doc)
        id_check = get_optional_str(doc, "idCheck")
        playlists = get_playlists(doc)
        raw_title = doc.get("rawTitle", "")
        raw_description = doc.get("rawDescription", "")
        created_at = to_utc(doc.get("createdAt"))
        updated_at = to_utc(doc.get("updatedAt"))

        rows.append((
            track_id, url, title, artist, album, start_at, end_at,
            fade_out_seconds, volume_change, status, id_check, playlists,
            raw_title, raw_description, created_at, updated_at,
        ))

    print(f"Inserting {len(rows)} tracks into PostgreSQL...")
    execute_values(cur, insert_sql, rows, template="""(
        %s::uuid, %s, %s, %s, %s, %s, %s,
        %s, %s, %s, %s, %s::text[],
        %s, %s, %s, %s
    )""")

    pg_conn.commit()
    cur.close()
    pg_conn.close()
    mongo_client.close()

    print(f"Done. Migrated {len(rows)} tracks.")


if __name__ == "__main__":
    main()
