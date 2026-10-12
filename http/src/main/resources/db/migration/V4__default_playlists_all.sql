-- Backfill: every track should belong to at least the 'all' playlist.
-- Rows with no playlists get {all}.
UPDATE tracks
SET playlists = ARRAY['all']
WHERE playlists IS NULL OR array_length(playlists, 1) IS NULL;

-- New rows default to {all} too (fresh tracks created via plain INSERT).
ALTER TABLE tracks ALTER COLUMN playlists SET DEFAULT ARRAY['all'];
