CREATE TABLE IF NOT EXISTS membership_plans (
 id uuid PRIMARY KEY, name varchar(120) NOT NULL, period varchar(10) NOT NULL CHECK (period IN ('MONTH','YEAR','PACKAGE')),
 entitlement_mode varchar(10) NOT NULL CHECK (entitlement_mode IN ('CREDITS','UNLIMITED')),
 credits int, validity_days int, price_minor bigint NOT NULL CHECK (price_minor BETWEEN 0 AND 1000000000000),
 currency varchar(3) NOT NULL CHECK (currency = 'THB'), active boolean NOT NULL,
 CHECK ((entitlement_mode='CREDITS' AND credits IS NOT NULL AND credits BETWEEN 1 AND 10000) OR (entitlement_mode='UNLIMITED' AND credits IS NULL)),
 CHECK ((period='PACKAGE' AND validity_days IS NOT NULL AND validity_days BETWEEN 1 AND 3660) OR (period IN ('MONTH','YEAR') AND validity_days IS NULL))
);
CREATE TABLE IF NOT EXISTS plan_services (
 plan_id uuid NOT NULL REFERENCES membership_plans(id), service_id uuid NOT NULL REFERENCES services(id),
 PRIMARY KEY(plan_id,service_id)
);
ALTER TABLE membership_grants DROP CONSTRAINT membership_grants_source_check;
ALTER TABLE membership_grants ADD CONSTRAINT membership_grants_source_check CHECK (source IN ('SYNTHETIC_FIXTURE','ADMIN_COMPLIMENTARY'));
ALTER TABLE membership_grants ADD COLUMN entitlement_mode varchar(10) NOT NULL DEFAULT 'CREDITS' CHECK (entitlement_mode IN ('CREDITS','UNLIMITED'));
ALTER TABLE membership_grants ADD COLUMN plan_id uuid REFERENCES membership_plans(id);
ALTER TABLE membership_grants ADD COLUMN plan_snapshot jsonb;
ALTER TABLE membership_grants ALTER COLUMN remaining_credits DROP NOT NULL;
ALTER TABLE membership_grants ADD CONSTRAINT grant_mode_balance CHECK (
 (entitlement_mode='CREDITS' AND remaining_credits IS NOT NULL AND remaining_credits>=0)
 OR (entitlement_mode='UNLIMITED' AND remaining_credits IS NULL));
ALTER TABLE membership_grants ADD CONSTRAINT complimentary_plan_required CHECK (source='SYNTHETIC_FIXTURE' OR (plan_id IS NOT NULL AND plan_snapshot IS NOT NULL));
CREATE TABLE IF NOT EXISTS complimentary_requests (
 actor_id uuid NOT NULL REFERENCES users(id), request_key varchar(80) NOT NULL,
 customer_id uuid NOT NULL REFERENCES users(id), plan_id uuid NOT NULL REFERENCES membership_plans(id),
 starts_at timestamptz NOT NULL, reason varchar(250) NOT NULL, grant_id uuid REFERENCES membership_grants(id),
 PRIMARY KEY(actor_id,request_key)
);
CREATE TABLE IF NOT EXISTS trainer_availability (
 id uuid PRIMARY KEY, trainer_id uuid NOT NULL REFERENCES users(id), starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
 CHECK (ends_at>starts_at AND ends_at<=starts_at+interval '31 days'),
 EXCLUDE USING gist (trainer_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&)
);
CREATE INDEX trainer_availability_dates ON trainer_availability(trainer_id,starts_at);
ALTER TABLE class_sessions DROP CONSTRAINT class_sessions_state_check;
ALTER TABLE class_sessions ADD CONSTRAINT class_sessions_state_check CHECK (state IN ('DRAFT','PUBLISHED','CANCELLED','COMPLETED'));
ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check CHECK (status IN ('CONFIRMED','CANCELLED','ATTENDED','NO_SHOW'));
ALTER TABLE bookings DROP CONSTRAINT bookings_cost_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_cost_check CHECK (cost IN (0,1));
DROP INDEX one_active_booking;
CREATE UNIQUE INDEX one_active_booking ON bookings(customer_id,session_id) WHERE status <> 'CANCELLED';
CREATE TABLE IF NOT EXISTS attendance_records (
 booking_id uuid PRIMARY KEY REFERENCES bookings(id), recorded_by uuid NOT NULL REFERENCES users(id),
 outcome varchar(2000) NOT NULL, recorded_at timestamptz NOT NULL
);
CREATE TABLE IF NOT EXISTS reviews (
 id uuid PRIMARY KEY, booking_id uuid NOT NULL UNIQUE REFERENCES bookings(id), customer_id uuid NOT NULL REFERENCES users(id),
 rating int NOT NULL CHECK(rating BETWEEN 1 AND 5), body varchar(2000) NOT NULL, created_at timestamptz NOT NULL
);
ALTER TABLE bookings ADD CONSTRAINT booking_customer_pair UNIQUE(id,customer_id);
ALTER TABLE reviews ADD CONSTRAINT review_owner FOREIGN KEY(booking_id,customer_id) REFERENCES bookings(id,customer_id);
CREATE INDEX review_customer ON reviews(customer_id,created_at DESC);

-- Server-owned application tables: client API roles have no row policies.
ALTER TABLE membership_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE plan_services ENABLE ROW LEVEL SECURITY;
ALTER TABLE complimentary_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE trainer_availability ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE reviews ENABLE ROW LEVEL SECURITY;
