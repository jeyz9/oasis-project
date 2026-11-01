-- Run only after approval on the verified target project/database.
-- Creates an empty private schema; does not adopt or alter public tables.
BEGIN;
SET LOCAL lock_timeout = '5s';
DO $$
BEGIN
 IF to_regnamespace('oasis_app') IS NOT NULL THEN
  RAISE EXCEPTION 'oasis_app already exists; inspect it before proceeding';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
  RAISE EXCEPTION 'Required btree_gist extension must be reviewed/installed first';
 END IF;
END $$;
CREATE SCHEMA oasis_app;
REVOKE ALL ON SCHEMA oasis_app FROM PUBLIC;
DO $$
DECLARE role_name text;
BEGIN
 FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated'] LOOP
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
   EXECUTE format('REVOKE ALL ON SCHEMA oasis_app FROM %I', role_name);
  END IF;
 END LOOP;
END $$;
COMMIT;
