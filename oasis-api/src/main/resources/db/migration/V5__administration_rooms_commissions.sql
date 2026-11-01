CREATE TABLE IF NOT EXISTS business_settings (
 id int PRIMARY KEY CHECK(id=1), cancellation_minutes int CHECK(cancellation_minutes BETWEEN 0 AND 10080)
);
INSERT INTO business_settings VALUES (1,NULL);
CREATE TABLE IF NOT EXISTS rooms (
 id uuid PRIMARY KEY, name varchar(120) NOT NULL, location varchar(120) NOT NULL,
 capacity int NOT NULL CHECK(capacity BETWEEN 1 AND 100), active boolean NOT NULL,
 canonical_name text GENERATED ALWAYS AS (lower(btrim(name))) STORED UNIQUE
);
INSERT INTO rooms(id,name,location,capacity,active)
 SELECT gen_random_uuid(),min(btrim(location)),min(btrim(location)),100,true
 FROM class_sessions GROUP BY lower(btrim(location));
ALTER TABLE class_sessions ADD COLUMN room_id uuid REFERENCES rooms(id);
UPDATE class_sessions s SET room_id=r.id FROM rooms r WHERE lower(btrim(s.location))=r.canonical_name;
ALTER TABLE class_sessions ALTER COLUMN room_id SET NOT NULL;
ALTER TABLE class_sessions ADD CONSTRAINT canonical_room_schedule EXCLUDE USING gist
 (room_id WITH =,tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE(state='PUBLISHED');
CREATE TABLE IF NOT EXISTS trainer_terms (
 trainer_id uuid PRIMARY KEY REFERENCES users(id), commission_minor bigint NOT NULL CHECK(commission_minor BETWEEN 0 AND 1000000000)
);
ALTER TABLE class_sessions ADD COLUMN commission_minor bigint NOT NULL DEFAULT 0 CHECK(commission_minor BETWEEN 0 AND 1000000000);

-- Server-owned application tables: client API roles have no row policies.
ALTER TABLE business_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE trainer_terms ENABLE ROW LEVEL SECURITY;
