CREATE TABLE IF NOT EXISTS quota_usage (
  day TEXT NOT NULL,
  provider TEXT NOT NULL,
  client_id TEXT NOT NULL,
  requests INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, provider, client_id)
);
CREATE INDEX IF NOT EXISTS quota_usage_day_provider ON quota_usage(day, provider);


-- Durable BOZER ACTION mission queue. Each scheduled tick advances one
-- bounded cognitive work package; mission output and checkpoints survive restarts.
CREATE TABLE IF NOT EXISTS missions (
  mission_id TEXT PRIMARY KEY,
  client_id TEXT NOT NULL,
  objective TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('queued','running','completed','failed','cancelled')),
  plan_json TEXT NOT NULL,
  results_json TEXT NOT NULL DEFAULT '[]',
  current_step INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  next_run_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  cancel_requested INTEGER NOT NULL DEFAULT 0,
  last_error TEXT
);
CREATE INDEX IF NOT EXISTS missions_due
  ON missions(status, next_run_at, expires_at);
CREATE INDEX IF NOT EXISTS missions_client_created
  ON missions(client_id, created_at DESC);
