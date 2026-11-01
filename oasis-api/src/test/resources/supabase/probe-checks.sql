-- This fragment executes only inside the rollback-isolated probe builder's nested block.
-- It tests database compatibility/constraints, not HTTP authorization or concurrent JPA requests.
SELECT count(*) INTO v_count FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname=v_schema AND c.relkind='r';
IF v_count<>28 THEN RAISE EXCEPTION 'Expected 28 application tables, found %',v_count; END IF;
v_checks:=v_checks||jsonb_build_array('six_migration_scripts_and_28_tables');

FOR v_record IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname=v_schema AND c.relkind='r' LOOP
 EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY',v_schema,v_record.relname);
END LOOP;
IF has_schema_privilege('anon',v_schema,'USAGE') OR has_schema_privilege('authenticated',v_schema,'USAGE') THEN
 RAISE EXCEPTION 'Probe schema must not be reachable by client roles';
END IF;
v_checks:=v_checks||jsonb_build_array('private_schema_denies_client_roles');

INSERT INTO users(id,email,password_hash,display_name) VALUES
 (v_customer,'customer@oasis-probe.test','not-a-login-hash','Synthetic customer'),
 (v_other,'other@oasis-probe.test','not-a-login-hash','Synthetic other'),
 (v_trainer,'trainer@oasis-probe.test','not-a-login-hash','Synthetic trainer'),
 (v_trainer2,'trainer2@oasis-probe.test','not-a-login-hash','Synthetic trainer 2');
INSERT INTO user_roles VALUES(v_customer,'CUSTOMER'),(v_other,'CUSTOMER'),(v_trainer,'TRAINER'),(v_trainer2,'TRAINER');
INSERT INTO services VALUES(v_service,'Probe Yoga','Synthetic SQL fixture','Yoga','GROUP',60,'BEGINNER',10,true);
INSERT INTO rooms(id,name,location,capacity,active) VALUES(v_room,'Probe room','Synthetic',10,true),(v_room2,'Second room','Synthetic',10,true);
INSERT INTO class_sessions(id,service_id,trainer_id,location,starts_at,ends_at,capacity,state,cancellation_minutes,room_id)
 VALUES(v_session,v_service,v_trainer,'Probe room',v_now+interval '3 hours',v_now+interval '4 hours',1,'PUBLISHED',120,v_room);
INSERT INTO membership_grants(id,customer_id,name,source,valid_from,valid_until,status,remaining_credits)
 VALUES(v_grant,v_customer,'Probe credits','SYNTHETIC_FIXTURE',v_now,v_now+interval '30 days','ACTIVE',10);
INSERT INTO grant_services VALUES(v_grant,v_service);
INSERT INTO credit_ledger(id,grant_id,kind,delta,reason,created_at) VALUES(gen_random_uuid(),v_grant,'GRANT',10,'Synthetic fixture',v_now);

PERFORM id FROM class_sessions WHERE id=v_session FOR NO KEY UPDATE;
PERFORM id FROM membership_grants WHERE id=v_grant FOR NO KEY UPDATE;
UPDATE membership_grants SET remaining_credits=remaining_credits-1 WHERE id=v_grant AND remaining_credits>0;
GET DIAGNOSTICS v_count=ROW_COUNT;
IF v_count<>1 THEN RAISE EXCEPTION 'Credit debit failed'; END IF;
UPDATE class_sessions SET reserved_count=reserved_count+1 WHERE id=v_session AND reserved_count<capacity;
GET DIAGNOSTICS v_count=ROW_COUNT;
IF v_count<>1 THEN RAISE EXCEPTION 'Seat reservation failed'; END IF;
INSERT INTO bookings(id,customer_id,session_id,grant_id,status,booked_at,starts_at,ends_at,cost)
 VALUES(v_booking,v_customer,v_session,v_grant,'CONFIRMED',v_now,v_now+interval '3 hours',v_now+interval '4 hours',1);
INSERT INTO credit_ledger(id,grant_id,booking_id,kind,delta,reason,created_at)
 VALUES(v_debit,v_grant,v_booking,'DEBIT',-1,'Synthetic confirmation',v_now);
IF (SELECT remaining_credits FROM membership_grants WHERE id=v_grant)<>9 THEN RAISE EXCEPTION 'Expected 9 credits'; END IF;
v_checks:=v_checks||jsonb_build_array('no_key_update_and_guarded_booking_debit');

UPDATE class_sessions SET reserved_count=reserved_count+1 WHERE id=v_session AND reserved_count<capacity;
GET DIAGNOSTICS v_count=ROW_COUNT;
IF v_count<>0 THEN RAISE EXCEPTION 'Full session reserved another seat'; END IF;
v_checks:=v_checks||jsonb_build_array('full_session_guard');

BEGIN
 INSERT INTO bookings(id,customer_id,session_id,grant_id,status,booked_at,starts_at,ends_at,cost)
 VALUES(gen_random_uuid(),v_customer,v_session,v_grant,'CONFIRMED',v_now,v_now+interval '3 hours',v_now+interval '4 hours',1);
 RAISE EXCEPTION 'Duplicate booking was accepted';
EXCEPTION WHEN unique_violation OR exclusion_violation THEN NULL;
END;
v_checks:=v_checks||jsonb_build_array('duplicate_booking_constraint');

BEGIN
 INSERT INTO bookings(id,customer_id,session_id,grant_id,status,booked_at,starts_at,ends_at,cost)
 VALUES(gen_random_uuid(),v_other,v_session,v_grant,'CONFIRMED',v_now,v_now+interval '3 hours',v_now+interval '4 hours',1);
 RAISE EXCEPTION 'Cross-owner grant was accepted';
EXCEPTION WHEN foreign_key_violation THEN NULL;
END;
v_checks:=v_checks||jsonb_build_array('grant_owner_foreign_key');

v_rejected:=false;
BEGIN UPDATE credit_ledger SET delta=-2 WHERE id=v_debit;
EXCEPTION WHEN raise_exception THEN v_rejected:=true;
END;
IF NOT v_rejected THEN RAISE EXCEPTION 'Ledger update was accepted'; END IF;
v_checks:=v_checks||jsonb_build_array('immutable_credit_ledger');

UPDATE bookings SET status='CANCELLED',cancelled_at=v_now+interval '1 hour' WHERE id=v_booking;
UPDATE class_sessions SET reserved_count=reserved_count-1 WHERE id=v_session AND reserved_count>0;
INSERT INTO credit_ledger(id,grant_id,booking_id,kind,delta,reverses_id,reason,created_at)
 VALUES(gen_random_uuid(),v_grant,v_booking,'RESTORE',1,v_debit,'Synthetic cancellation',v_now+interval '1 hour');
UPDATE membership_grants SET remaining_credits=remaining_credits+1 WHERE id=v_grant;
IF (SELECT remaining_credits FROM membership_grants WHERE id=v_grant)<>10 OR
 (SELECT reserved_count FROM class_sessions WHERE id=v_session)<>0 THEN RAISE EXCEPTION 'Restoration did not reconcile'; END IF;
BEGIN
 INSERT INTO credit_ledger(id,grant_id,booking_id,kind,delta,reverses_id,reason,created_at)
 VALUES(gen_random_uuid(),v_grant,v_booking,'RESTORE',1,v_debit,'Duplicate restore',v_now);
 RAISE EXCEPTION 'Duplicate restoration was accepted';
EXCEPTION WHEN unique_violation THEN NULL;
END;
v_checks:=v_checks||jsonb_build_array('credit_restoration_once');

BEGIN
 INSERT INTO rooms(id,name,location,capacity,active) VALUES(gen_random_uuid(),' probe ROOM ','Synthetic',10,true);
 RAISE EXCEPTION 'Duplicate canonical room was accepted';
EXCEPTION WHEN unique_violation THEN NULL;
END;
v_checks:=v_checks||jsonb_build_array('canonical_room_identity');

BEGIN
 INSERT INTO class_sessions(id,service_id,trainer_id,location,starts_at,ends_at,capacity,state,cancellation_minutes,room_id)
 VALUES(gen_random_uuid(),v_service,v_trainer2,'Case variant',v_now+interval '3 hours',v_now+interval '4 hours',1,'PUBLISHED',120,v_room);
 RAISE EXCEPTION 'Overlapping room was accepted';
EXCEPTION WHEN exclusion_violation THEN NULL;
END;
BEGIN
 INSERT INTO class_sessions(id,service_id,trainer_id,location,starts_at,ends_at,capacity,state,cancellation_minutes,room_id)
 VALUES(gen_random_uuid(),v_service,v_trainer,'Second room',v_now+interval '3 hours',v_now+interval '4 hours',1,'PUBLISHED',120,v_room2);
 RAISE EXCEPTION 'Overlapping trainer was accepted';
EXCEPTION WHEN exclusion_violation THEN NULL;
END;
v_checks:=v_checks||jsonb_build_array('room_and_trainer_interval_exclusion');

INSERT INTO membership_grants(id,customer_id,name,source,valid_from,valid_until,status,remaining_credits,entitlement_mode)
 VALUES(v_unlimited,v_other,'Unlimited probe','SYNTHETIC_FIXTURE',v_now,v_now+interval '30 days','ACTIVE',NULL,'UNLIMITED');
INSERT INTO grant_services VALUES(v_unlimited,v_service);
INSERT INTO class_sessions(id,service_id,trainer_id,location,starts_at,ends_at,capacity,state,cancellation_minutes,room_id,commission_minor)
 VALUES(v_session2,v_service,v_trainer,'Second room',v_now+interval '5 hours',v_now+interval '6 hours',2,'COMPLETED',120,v_room2,25000);
INSERT INTO bookings(id,customer_id,session_id,grant_id,status,booked_at,starts_at,ends_at,cost)
 VALUES(v_booking2,v_other,v_session2,v_unlimited,'ATTENDED',v_now,v_now+interval '5 hours',v_now+interval '6 hours',0);
UPDATE class_sessions SET reserved_count=1 WHERE id=v_session2;
IF (SELECT remaining_credits FROM membership_grants WHERE id=v_unlimited) IS NOT NULL THEN RAISE EXCEPTION 'Unlimited balance changed'; END IF;
INSERT INTO attendance_records VALUES(v_booking2,v_trainer,'Synthetic completion',v_now+interval '6 hours');
INSERT INTO reviews VALUES(gen_random_uuid(),v_booking2,v_other,5,'Synthetic review',v_now+interval '6 hours');
BEGIN
 INSERT INTO reviews VALUES(gen_random_uuid(),v_booking2,v_other,5,'Duplicate review',v_now+interval '6 hours');
 RAISE EXCEPTION 'Duplicate review accepted';
EXCEPTION WHEN unique_violation THEN NULL;
END;
v_checks:=v_checks||jsonb_build_array('unlimited_and_unique_review_storage');

INSERT INTO membership_plans VALUES(v_plan,'Probe plan','MONTH','CREDITS',10,NULL,150050,'THB',true);
INSERT INTO plan_services VALUES(v_plan,v_service);
INSERT INTO membership_grants(id,customer_id,name,source,valid_from,valid_until,status,remaining_credits,entitlement_mode,plan_id,plan_snapshot)
 VALUES(v_paid_grant,v_customer,'Probe plan','SIMULATED_PAYMENT',v_now,v_now+interval '1 month','ACTIVE',10,'CREDITS',v_plan,'{"name":"Probe plan"}');
INSERT INTO credit_ledger(id,grant_id,kind,delta,reason,created_at) VALUES(gen_random_uuid(),v_paid_grant,'GRANT',10,'Synthetic payment',v_now);
INSERT INTO membership_orders(id,customer_id,plan_id,request_key,name,period,entitlement_mode,credits,amount_minor,currency,plan_snapshot,status,simulated,created_at,expires_at)
 VALUES(v_order,v_customer,v_plan,'supabase-probe-order','Probe plan','MONTH','CREDITS',10,150050,'THB','{"name":"Probe plan"}','PENDING',true,v_now,v_now+interval '1 day');
INSERT INTO order_services VALUES(v_order,v_service);
INSERT INTO payments(id,order_id,provider,merchant_id,external_id,amount_minor,currency,status,disposition,simulated,captured_at)
 VALUES(v_payment,v_order,'SQL_PROBE','SYNTHETIC','probe-capture',150050,'THB','SUCCEEDED','ACCEPTED',true,'2030-01-01 16:59:59Z');
UPDATE membership_orders SET status='PAID',paid_at='2030-01-01 16:59:59Z',grant_id=v_paid_grant WHERE id=v_order;
INSERT INTO receipts VALUES(v_receipt,v_payment,v_order,'SIM-SUPABASE-PROBE',v_customer,'Synthetic customer','Probe plan',150050,'THB',true,'2030-01-01 16:59:59Z');
v_rejected:=false;
BEGIN UPDATE membership_orders SET amount_minor=1 WHERE id=v_order;
EXCEPTION WHEN raise_exception THEN v_rejected:=true;
END;
IF NOT v_rejected THEN RAISE EXCEPTION 'Paid order snapshot changed'; END IF;
v_rejected:=false;
BEGIN UPDATE receipts SET amount_minor=1 WHERE id=v_receipt;
EXCEPTION WHEN raise_exception THEN v_rejected:=true;
END;
IF NOT v_rejected THEN RAISE EXCEPTION 'Receipt changed'; END IF;
v_checks:=v_checks||jsonb_build_array('jsonb_integer_money_and_immutable_payment_snapshots');

INSERT INTO payment_events VALUES(gen_random_uuid(),'SQL_PROBE','SYNTHETIC','probe-event',repeat('a',64),v_order,'PAYMENT_SUCCEEDED','PROCESSED','SYNTHETIC',v_now);
INSERT INTO payment_events VALUES(gen_random_uuid(),'SQL_PROBE','SYNTHETIC','probe-event',repeat('a',64),v_order,'PAYMENT_SUCCEEDED','PROCESSED','SYNTHETIC',v_now)
 ON CONFLICT(provider,merchant_id,external_event_id) DO NOTHING;
GET DIAGNOSTICS v_count=ROW_COUNT;
IF v_count<>0 THEN RAISE EXCEPTION 'Duplicate payment event was inserted'; END IF;
BEGIN
 INSERT INTO payments(id,order_id,provider,merchant_id,external_id,amount_minor,currency,status,disposition,simulated,captured_at)
 VALUES(gen_random_uuid(),v_order,'SQL_PROBE','SYNTHETIC','second-capture',150050,'THB','SUCCEEDED','ACCEPTED',true,v_now);
 RAISE EXCEPTION 'Second accepted payment inserted';
EXCEPTION WHEN unique_violation THEN NULL;
END;
v_checks:=v_checks||jsonb_build_array('payment_event_deduplication_and_single_fulfillment');

INSERT INTO refunds(id,payment_id,actor_id,request_key,reason,amount_minor,state,created_at,confirmed_at)
 VALUES(gen_random_uuid(),v_payment,v_trainer,'probe-refund','Synthetic full refund',150050,'SUCCEEDED',v_now,'2030-01-01 17:00:00Z');
SELECT coalesce(sum(amount_minor),0) INTO v_amount FROM payments
 WHERE simulated AND disposition='ACCEPTED' AND captured_at>='2029-12-31 17:00:00Z' AND captured_at<'2030-01-01 17:00:00Z';
IF v_amount<>150050 THEN RAISE EXCEPTION 'Bangkok capture cutoff failed'; END IF;
SELECT coalesce(sum(amount_minor),0) INTO v_amount FROM refunds
 WHERE state='SUCCEEDED' AND confirmed_at>='2030-01-01 17:00:00Z' AND confirmed_at<'2030-01-02 17:00:00Z';
IF v_amount<>150050 THEN RAISE EXCEPTION 'Bangkok refund cutoff failed'; END IF;
v_checks:=v_checks||jsonb_build_array('bangkok_cash_flow_periods');

INSERT INTO notification_jobs(id,owner_id,booking_id,kind,channel,dedupe_key,title,body,state,next_attempt_at,created_at)
 VALUES(v_job,v_other,v_booking2,'BOOKING_CONFIRMED','INAPP','probe-job','Synthetic','Synthetic','PENDING',v_now,v_now);
INSERT INTO notification_jobs(id,owner_id,booking_id,kind,channel,dedupe_key,title,body,state,next_attempt_at,created_at)
 VALUES(gen_random_uuid(),v_other,v_booking2,'BOOKING_CONFIRMED','INAPP','probe-job','Synthetic','Synthetic','PENDING',v_now,v_now)
 ON CONFLICT(dedupe_key) DO NOTHING;
GET DIAGNOSTICS v_count=ROW_COUNT;
IF v_count<>0 THEN RAISE EXCEPTION 'Duplicate notification inserted'; END IF;
SELECT id INTO v_claimed FROM notification_jobs WHERE state='PENDING' AND next_attempt_at<=v_now FOR UPDATE SKIP LOCKED LIMIT 1;
IF v_claimed IS DISTINCT FROM v_job THEN RAISE EXCEPTION 'Outbox claim failed'; END IF;
UPDATE notification_jobs SET state='PROCESSING',attempts=attempts+1,lease_token=gen_random_uuid(),lease_until=v_now+interval '60 seconds' WHERE id=v_job;
INSERT INTO notifications VALUES(v_job,v_other,'Synthetic','Synthetic',v_now,NULL);
INSERT INTO notifications VALUES(v_job,v_other,'Synthetic','Synthetic',v_now,NULL) ON CONFLICT DO NOTHING;
IF (SELECT count(*) FROM notifications)<>1 THEN RAISE EXCEPTION 'Inbox deduplication failed'; END IF;
v_checks:=v_checks||jsonb_build_array('skip_locked_outbox_and_inbox_deduplication');

IF EXISTS(SELECT 1 FROM membership_grants g WHERE entitlement_mode='CREDITS' AND remaining_credits<>(SELECT coalesce(sum(delta),0) FROM credit_ledger l WHERE l.grant_id=g.id)) THEN RAISE EXCEPTION 'Credits do not reconcile'; END IF;
IF EXISTS(SELECT 1 FROM class_sessions s WHERE reserved_count<>(SELECT count(*) FROM bookings b WHERE b.session_id=s.id AND b.status<>'CANCELLED')) THEN RAISE EXCEPTION 'Seats do not reconcile'; END IF;
v_checks:=v_checks||jsonb_build_array('final_credit_and_seat_reconciliation');
