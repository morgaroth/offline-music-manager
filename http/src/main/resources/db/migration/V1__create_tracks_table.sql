CREATE TABLE IF NOT EXISTS tracks (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  url         TEXT NOT NULL DEFAULT '',
  title       TEXT NOT NULL DEFAULT '',
  artist      TEXT NOT NULL DEFAULT '',
  album       TEXT NOT NULL DEFAULT '',
  start_at    TEXT,
  end_at      TEXT,
  fade_out_seconds INTEGER,
  volume_change NUMERIC,
  status      TEXT NOT NULL DEFAULT 'draft',
  id_check    TEXT,
  playlists   TEXT[] NOT NULL DEFAULT '{}',
  raw_title   TEXT NOT NULL DEFAULT '',
  raw_description TEXT NOT NULL DEFAULT '',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tracks_status ON tracks(status);
CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist);
CREATE INDEX IF NOT EXISTS idx_tracks_title ON tracks(title);
CREATE UNIQUE INDEX IF NOT EXISTS idx_tracks_id_check ON tracks(id_check) WHERE id_check IS NOT NULL;
