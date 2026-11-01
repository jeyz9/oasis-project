CREATE TABLE IF NOT EXISTS notification_preferences (
 user_id uuid PRIMARY KEY REFERENCES users(id), email_enabled boolean NOT NULL DEFAULT false,
 line_enabled boolean NOT NULL DEFAULT false, line_destination varchar(200)
);
CREATE TABLE IF NOT EXISTS notification_jobs (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES users(id), booking_id uuid REFERENCES bookings(id),
 kind varchar(30) NOT NULL CHECK(kind IN ('BOOKING_CONFIRMED','BOOKING_CANCELLED','CLASS_REMINDER')),
 channel varchar(10) NOT NULL CHECK(channel IN ('INAPP','EMAIL','LINE')),
 dedupe_key varchar(150) NOT NULL UNIQUE, title varchar(160) NOT NULL, body varchar(1000) NOT NULL,
 state varchar(12) NOT NULL CHECK(state IN ('PENDING','PROCESSING','DELIVERED','RETRY','BLOCKED','DEAD','CANCELLED')),
 attempts int NOT NULL DEFAULT 0 CHECK(attempts>=0), next_attempt_at timestamptz NOT NULL,
 lease_until timestamptz, lease_token uuid, last_code varchar(80), simulated boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL, delivered_at timestamptz
);
CREATE INDEX notification_due ON notification_jobs(state,next_attempt_at);
CREATE TABLE IF NOT EXISTS notifications (
 id uuid PRIMARY KEY REFERENCES notification_jobs(id), owner_id uuid NOT NULL REFERENCES users(id),
 title varchar(160) NOT NULL, body varchar(1000) NOT NULL, created_at timestamptz NOT NULL, read_at timestamptz
);
CREATE INDEX notification_inbox ON notifications(owner_id,created_at DESC);

-- Server-owned application tables: client API roles have no row policies.
ALTER TABLE notification_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
