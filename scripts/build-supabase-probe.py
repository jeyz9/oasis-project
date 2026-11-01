"""Build a rollback-only SQL compatibility probe; it never connects or reads credentials."""
from pathlib import Path
import hashlib
import json
import uuid

root = Path(__file__).resolve().parent.parent
name = 'oasis_probe_' + uuid.uuid4().hex
migrations = sorted((root / 'oasis-api/src/main/resources/db/migration').glob('V*.sql'))
if len(migrations) != 6:
    raise SystemExit('Review the probe before testing a different migration set.')
manifest = [{'file': p.name, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in migrations]
sql = f"""-- Generated from unchanged application migrations; no existing schema/data is migrated.
CREATE FUNCTION pg_temp.{name}() RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER
SET search_path=pg_catalog,pg_temp AS $oasis_probe_body$
DECLARE
 v_schema text := '{name}';
 v_checks jsonb := '[]'; v_result jsonb; v_record record;
 v_count bigint; v_amount bigint; v_rejected boolean; v_claimed uuid;
 v_public_before oid[]; v_public_after oid[];
 v_now timestamptz := '2030-01-01 00:00:00Z';
 v_customer uuid := gen_random_uuid(); v_other uuid := gen_random_uuid();
 v_trainer uuid := gen_random_uuid(); v_trainer2 uuid := gen_random_uuid();
 v_service uuid := gen_random_uuid(); v_room uuid := gen_random_uuid(); v_room2 uuid := gen_random_uuid();
 v_session uuid := gen_random_uuid(); v_session2 uuid := gen_random_uuid();
 v_grant uuid := gen_random_uuid(); v_unlimited uuid := gen_random_uuid(); v_paid_grant uuid := gen_random_uuid();
 v_booking uuid := gen_random_uuid(); v_booking2 uuid := gen_random_uuid(); v_debit uuid := gen_random_uuid();
 v_plan uuid := gen_random_uuid(); v_order uuid := gen_random_uuid(); v_payment uuid := gen_random_uuid();
 v_receipt uuid := gen_random_uuid(); v_job uuid := gen_random_uuid();
BEGIN
 IF to_regnamespace(v_schema) IS NOT NULL THEN RAISE EXCEPTION 'Probe name already exists'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_extension WHERE extname='btree_gist') THEN RAISE EXCEPTION 'Install/review required btree_gist separately'; END IF;
 SELECT array_agg(c.oid ORDER BY c.oid) INTO v_public_before FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r';
 BEGIN
  EXECUTE format('CREATE SCHEMA %I',v_schema);
  EXECUTE format('REVOKE ALL ON SCHEMA %I FROM PUBLIC, anon, authenticated',v_schema);
  PERFORM set_config('search_path',quote_ident(v_schema)||',public,extensions,pg_catalog',true);
  PERFORM set_config('lock_timeout','5s',true);
"""
for i, migration in enumerate(migrations, 1):
    delimiter = f'$oasis_v{i}$'
    content = migration.read_text(encoding='utf-8')
    if delimiter in content:
        raise SystemExit('Unsafe generated dollar delimiter.')
    sql += f'  EXECUTE {delimiter}\n{content}\n{delimiter};\n'
sql += (root / 'oasis-api/src/test/resources/supabase/probe-checks.sql').read_text(encoding='utf-8')
sql += """
  v_result:=jsonb_build_object('status','PASS','postgres_version',current_setting('server_version'),
    'migration_scripts',6,'application_tables',28,'checks',v_checks,'checks_passed',jsonb_array_length(v_checks));
  RAISE EXCEPTION USING ERRCODE='ZP001',MESSAGE='Rollback isolated Oasis compatibility probe';
 EXCEPTION WHEN SQLSTATE 'ZP001' THEN NULL;
 END;
 IF v_result IS NULL OR to_regnamespace(v_schema) IS NOT NULL THEN RAISE EXCEPTION 'Probe did not roll back'; END IF;
 SELECT array_agg(c.oid ORDER BY c.oid) INTO v_public_after FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r';
 IF v_public_before IS DISTINCT FROM v_public_after THEN RAISE EXCEPTION 'Existing public tables changed'; END IF;
 RETURN v_result||jsonb_build_object('rolled_back',true,'scratch_schema_absent',true,'public_table_oids_unchanged',true);
END
$oasis_probe_body$;
"""
sql += f'SELECT pg_temp.{name}() AS oasis_supabase_probe;\n'
output = root / '.local/supabase-probe.sql'
output.parent.mkdir(exist_ok=True)
output.write_text(sql, encoding='utf-8', newline='\n')
(root / '.local/supabase-probe-manifest.json').write_text(json.dumps({'schema': name, 'migrations': manifest}, indent=2), encoding='utf-8')
print(json.dumps({'sql': str(output), 'migration_scripts': 6, 'schema': name, 'rollback_only': True}))
