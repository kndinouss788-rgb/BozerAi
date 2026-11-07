CREATE TABLE IF NOT EXISTS quota_usage (
  day TEXT NOT NULL,
  provider TEXT NOT NULL,
  client_id TEXT NOT NULL,
  requests INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, provider, client_id)
);
CREATE INDEX IF NOT EXISTS quota_usage_day_provider ON quota_usage(day, provider);
