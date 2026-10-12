-- Durable fetch queue: one row per track to pull. The orchestrator claims
-- pending rows, marks them running, then done/errored. Survives restarts.
CREATE TABLE IF NOT EXISTS fetch_queue (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  track_id    UUID NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
  status      TEXT NOT NULL DEFAULT 'pending',   -- pending | running | done | errored
  started_at  TIMESTAMPTZ,
  error       TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_fetch_queue_status ON fetch_queue(status);

-- At most one queue row per track (avoid duplicate enqueues of the same track).
CREATE UNIQUE INDEX IF NOT EXISTS idx_fetch_queue_track ON fetch_queue(track_id);
