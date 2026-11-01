-- DESTRUCTIVE: explicitly approved reset of the ten original Oasis tables.
-- Reviewed target: Supabase zixzqrsrnthgszgxqnfk. Verify target externally first.
-- This script refuses drift, history, nonempty tables, or dependent external objects.
BEGIN;
SET LOCAL lock_timeout = '5s';
LOCK TABLE public.audit_events, public.booking_requests, public.bookings,
 public.class_sessions, public.credit_ledger, public.grant_services,
 public.membership_grants, public.services, public.user_roles, public.users
 IN ACCESS EXCLUSIVE MODE;
DO $$
DECLARE actual_tables text[]; row_count bigint;
BEGIN
 SELECT array_agg(c.relname::text ORDER BY c.relname) INTO actual_tables
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind IN ('r','p');
 IF actual_tables IS DISTINCT FROM ARRAY['audit_events','booking_requests','bookings',
  'class_sessions','credit_ledger','grant_services','membership_grants','services',
  'user_roles','users']::text[] THEN
  RAISE EXCEPTION 'Unexpected public tables; reset refused';
 END IF;
 SELECT (SELECT count(*) FROM public.audit_events)+(SELECT count(*) FROM public.booking_requests)
  +(SELECT count(*) FROM public.bookings)+(SELECT count(*) FROM public.class_sessions)
  +(SELECT count(*) FROM public.credit_ledger)+(SELECT count(*) FROM public.grant_services)
  +(SELECT count(*) FROM public.membership_grants)+(SELECT count(*) FROM public.services)
  +(SELECT count(*) FROM public.user_roles)+(SELECT count(*) FROM public.users) INTO row_count;
 IF row_count <> 0 THEN
  RAISE EXCEPTION 'New data appeared since the approved empty-table preflight; reset refused';
 END IF;
END $$;
-- No CASCADE: dependencies outside this exact set cause rollback.
DROP TABLE public.audit_events, public.booking_requests, public.bookings,
 public.class_sessions, public.credit_ledger, public.grant_services,
 public.membership_grants, public.services, public.user_roles, public.users RESTRICT;
-- This V1 trigger function must be recreated by the migration.
DROP FUNCTION public.reject_history_mutation() RESTRICT;
COMMIT;
