-- Fetch priority: higher runs sooner. Fetch-tab batch = 0 (normal),
-- single-track "Fetch now" from Edit = 1 (jump the line).
ALTER TABLE fetch_queue ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 0;

-- Claim order index: priority desc, then FIFO.
CREATE INDEX IF NOT EXISTS idx_fetch_queue_claim ON fetch_queue(status, priority DESC, created_at);
