CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS users (
 id uuid PRIMARY KEY, email varchar(254) NOT NULL UNIQUE CHECK (email = lower(trim(email))),
 password_hash varchar(100) NOT NULL, display_name varchar(100) NOT NULL,
 enabled boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS user_roles (
 user_id uuid NOT NULL REFERENCES users(id), role varchar(10) NOT NULL CHECK (role IN ('CUSTOMER','TRAINER','ADMIN')),
 PRIMARY KEY (user_id,role)
);
CREATE TABLE IF NOT EXISTS services (
 id uuid PRIMARY KEY, name varchar(120) NOT NULL, description varchar(2000) NOT NULL,
 category varchar(60) NOT NULL, kind varchar(10) NOT NULL CHECK (kind IN ('GROUP','PRIVATE')),
 duration_minutes int NOT NULL CHECK (duration_minutes BETWEEN 5 AND 480),
 difficulty varchar(20) NOT NULL CHECK (difficulty IN ('BEGINNER','INTERMEDIATE','ADVANCED','ALL_LEVELS')),
 capacity int NOT NULL CHECK (capacity BETWEEN 1 AND 100), active boolean NOT NULL,
 CHECK (kind <> 'PRIVATE' OR capacity = 1)
);
CREATE TABLE IF NOT EXISTS class_sessions (
 id uuid PRIMARY KEY, service_id uuid NOT NULL REFERENCES services(id), trainer_id uuid NOT NULL REFERENCES users(id),
 location varchar(120) NOT NULL, starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
 capacity int NOT NULL CHECK (capacity BETWEEN 1 AND 100), reserved_count int NOT NULL DEFAULT 0,
 state varchar(12) NOT NULL CHECK (state IN ('DRAFT','PUBLISHED','CANCELLED')),
 cancellation_minutes int NOT NULL CHECK (cancellation_minutes BETWEEN 0 AND 10080),
 CHECK (ends_at > starts_at), CHECK (reserved_count BETWEEN 0 AND capacity),
 EXCLUDE USING gist (trainer_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE (state = 'PUBLISHED'),
 EXCLUDE USING gist (location WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE (state = 'PUBLISHED')
);
CREATE INDEX session_dates ON class_sessions(state,starts_at);
CREATE TABLE IF NOT EXISTS membership_grants (
 id uuid PRIMARY KEY, customer_id uuid NOT NULL REFERENCES users(id), name varchar(120) NOT NULL,
 source varchar(30) NOT NULL CHECK (source = 'SYNTHETIC_FIXTURE'),
 valid_from timestamptz NOT NULL, valid_until timestamptz NOT NULL,
 status varchar(12) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED')),
 remaining_credits int NOT NULL CHECK (remaining_credits >= 0),
 CHECK (valid_until > valid_from), UNIQUE(id,customer_id)
);
CREATE TABLE IF NOT EXISTS grant_services (
 grant_id uuid NOT NULL REFERENCES membership_grants(id), service_id uuid NOT NULL REFERENCES services(id),
 PRIMARY KEY(grant_id,service_id)
);
CREATE TABLE IF NOT EXISTS bookings (
 id uuid PRIMARY KEY, customer_id uuid NOT NULL REFERENCES users(id), session_id uuid NOT NULL REFERENCES class_sessions(id),
 grant_id uuid NOT NULL, status varchar(12) NOT NULL CHECK (status IN ('CONFIRMED','CANCELLED')),
 booked_at timestamptz NOT NULL, cancelled_at timestamptz, cost int NOT NULL DEFAULT 1 CHECK (cost = 1),
 starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
 FOREIGN KEY (grant_id,customer_id) REFERENCES membership_grants(id,customer_id),
 UNIQUE (id,grant_id), CHECK (ends_at > starts_at),
 CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
 EXCLUDE USING gist (customer_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE (status = 'CONFIRMED')
);
CREATE UNIQUE INDEX one_active_booking ON bookings(customer_id,session_id) WHERE status = 'CONFIRMED';
CREATE INDEX customer_history ON bookings(customer_id,booked_at DESC);
CREATE INDEX session_bookings ON bookings(session_id,status);
CREATE TABLE IF NOT EXISTS credit_ledger (
 id uuid PRIMARY KEY, grant_id uuid NOT NULL REFERENCES membership_grants(id), booking_id uuid,
 kind varchar(10) NOT NULL CHECK (kind IN ('GRANT','DEBIT','RESTORE')), delta int NOT NULL,
 reverses_id uuid UNIQUE, reason varchar(250) NOT NULL, created_at timestamptz NOT NULL,
 UNIQUE(id,grant_id,booking_id),
 FOREIGN KEY(booking_id,grant_id) REFERENCES bookings(id,grant_id),
 FOREIGN KEY(reverses_id,grant_id,booking_id) REFERENCES credit_ledger(id,grant_id,booking_id),
 CHECK ((kind = 'GRANT' AND delta > 0 AND booking_id IS NULL AND reverses_id IS NULL)
 OR (kind = 'DEBIT' AND delta = -1 AND booking_id IS NOT NULL AND reverses_id IS NULL)
 OR (kind = 'RESTORE' AND delta = 1 AND booking_id IS NOT NULL AND reverses_id IS NOT NULL))
);
CREATE UNIQUE INDEX one_booking_entry ON credit_ledger(booking_id,kind) WHERE booking_id IS NOT NULL;
CREATE INDEX grant_ledger ON credit_ledger(grant_id,created_at);
CREATE TABLE IF NOT EXISTS booking_requests (
 customer_id uuid NOT NULL REFERENCES users(id), request_key varchar(80) NOT NULL,
 session_id uuid NOT NULL REFERENCES class_sessions(id), grant_id uuid NOT NULL REFERENCES membership_grants(id),
 booking_id uuid REFERENCES bookings(id), created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(customer_id,request_key)
);
CREATE TABLE IF NOT EXISTS audit_events (
 id uuid PRIMARY KEY, actor_id uuid NOT NULL REFERENCES users(id), action varchar(60) NOT NULL,
 resource_id uuid NOT NULL, reason varchar(250) NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);

-- Accounting entries and audit events are append-only, including for application code.
CREATE FUNCTION reject_history_mutation() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN RAISE EXCEPTION 'history is append only'; END $$;
CREATE TRIGGER immutable_ledger BEFORE UPDATE OR DELETE ON credit_ledger FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE ON audit_events FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();

-- Server-owned application tables: client API roles have no row policies.
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE services ENABLE ROW LEVEL SECURITY;
ALTER TABLE class_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership_grants ENABLE ROW LEVEL SECURITY;
ALTER TABLE grant_services ENABLE ROW LEVEL SECURITY;
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE credit_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
