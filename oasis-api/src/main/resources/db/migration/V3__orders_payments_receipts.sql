CREATE TABLE IF NOT EXISTS membership_orders (
 id uuid PRIMARY KEY, customer_id uuid NOT NULL REFERENCES users(id), plan_id uuid NOT NULL REFERENCES membership_plans(id),
 request_key varchar(80) NOT NULL, name varchar(120) NOT NULL,
 period varchar(10) NOT NULL CHECK(period IN ('MONTH','YEAR','PACKAGE')),
 entitlement_mode varchar(10) NOT NULL CHECK(entitlement_mode IN ('CREDITS','UNLIMITED')),
 credits int, validity_days int, amount_minor bigint NOT NULL CHECK(amount_minor>0 AND amount_minor<=1000000000000),
 currency varchar(3) NOT NULL CHECK(currency='THB'), plan_snapshot jsonb NOT NULL,
 status varchar(12) NOT NULL CHECK(status IN ('PENDING','PAID','CANCELLED','REVIEW')),
 simulated boolean NOT NULL, created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL,
 paid_at timestamptz, grant_id uuid UNIQUE REFERENCES membership_grants(id),
 UNIQUE(customer_id,request_key), CHECK(expires_at>created_at),
 CHECK((status='PAID')=(paid_at IS NOT NULL AND grant_id IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS order_services (
 order_id uuid NOT NULL REFERENCES membership_orders(id), service_id uuid NOT NULL REFERENCES services(id),
 PRIMARY KEY(order_id,service_id)
);
CREATE INDEX order_customer_history ON membership_orders(customer_id,created_at DESC);
CREATE TABLE IF NOT EXISTS payments (
 id uuid PRIMARY KEY, order_id uuid NOT NULL REFERENCES membership_orders(id), provider varchar(50) NOT NULL,
 merchant_id varchar(100) NOT NULL, external_id varchar(150) NOT NULL, amount_minor bigint NOT NULL CHECK(amount_minor>0),
 currency varchar(3) NOT NULL, status varchar(12) NOT NULL CHECK(status IN ('FAILED','SUCCEEDED')),
 disposition varchar(12) NOT NULL CHECK(disposition IN ('ACCEPTED','REVIEW','NONE')),
 simulated boolean NOT NULL, captured_at timestamptz,
 UNIQUE(provider,merchant_id,external_id), CHECK(status<>'SUCCEEDED' OR captured_at IS NOT NULL)
);
CREATE UNIQUE INDEX one_fulfillment_payment ON payments(order_id) WHERE disposition='ACCEPTED';
CREATE TABLE IF NOT EXISTS payment_events (
 id uuid PRIMARY KEY, provider varchar(50) NOT NULL, merchant_id varchar(100) NOT NULL, external_event_id varchar(150) NOT NULL,
 payload_hash varchar(64) NOT NULL, order_id uuid NOT NULL REFERENCES membership_orders(id),
 event_type varchar(30) NOT NULL, state varchar(12) NOT NULL CHECK(state IN ('PROCESSED','REJECTED','REVIEW')),
 code varchar(80) NOT NULL, received_at timestamptz NOT NULL,
 UNIQUE(provider,merchant_id,external_event_id)
);
CREATE INDEX payment_event_history ON payment_events(received_at DESC);
CREATE TABLE IF NOT EXISTS receipts (
 id uuid PRIMARY KEY, payment_id uuid NOT NULL UNIQUE REFERENCES payments(id), order_id uuid NOT NULL UNIQUE REFERENCES membership_orders(id),
 receipt_number varchar(80) NOT NULL UNIQUE, customer_id uuid NOT NULL REFERENCES users(id),
 customer_name varchar(100) NOT NULL, plan_name varchar(120) NOT NULL,
 amount_minor bigint NOT NULL CHECK(amount_minor>0), currency varchar(3) NOT NULL,
 simulated boolean NOT NULL, issued_at timestamptz NOT NULL
);
CREATE TRIGGER immutable_receipts BEFORE UPDATE OR DELETE ON receipts FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TABLE IF NOT EXISTS refunds (
 id uuid PRIMARY KEY, payment_id uuid NOT NULL UNIQUE REFERENCES payments(id), actor_id uuid NOT NULL REFERENCES users(id),
 request_key varchar(80) NOT NULL, reason varchar(250) NOT NULL, amount_minor bigint NOT NULL CHECK(amount_minor>0),
 state varchar(12) NOT NULL CHECK(state IN ('PENDING','SUCCEEDED','FAILED')),
 external_id varchar(150), created_at timestamptz NOT NULL, confirmed_at timestamptz,
 UNIQUE(actor_id,request_key), UNIQUE(external_id),
 CHECK((state='PENDING')=(confirmed_at IS NULL))
);
ALTER TABLE membership_grants DROP CONSTRAINT membership_grants_source_check;
ALTER TABLE membership_grants ADD CONSTRAINT membership_grants_source_check
 CHECK(source IN ('SYNTHETIC_FIXTURE','ADMIN_COMPLIMENTARY','SIMULATED_PAYMENT','VERIFIED_PAYMENT'));

-- Server-owned application tables: client API roles have no row policies.
ALTER TABLE membership_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_services ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE refunds ENABLE ROW LEVEL SECURITY;
