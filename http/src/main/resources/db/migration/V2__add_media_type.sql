-- Media type per track: 'music' (extracted mp3) or 'video' (H.264/AAC mp4).
-- Existing rows are all music, so default to that.
ALTER TABLE tracks ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'music';

CREATE INDEX IF NOT EXISTS idx_tracks_type ON tracks(type);
