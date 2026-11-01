ALTER TABLE membership_orders ADD CONSTRAINT order_customer_pair UNIQUE(id,customer_id);
ALTER TABLE payments ADD CONSTRAINT payment_order_pair UNIQUE(id,order_id);
ALTER TABLE receipts ADD CONSTRAINT receipt_payment_order FOREIGN KEY(payment_id,order_id) REFERENCES payments(id,order_id);
ALTER TABLE receipts ADD CONSTRAINT receipt_order_owner FOREIGN KEY(order_id,customer_id) REFERENCES membership_orders(id,customer_id);
ALTER TABLE membership_orders ADD CONSTRAINT order_grant_owner FOREIGN KEY(grant_id,customer_id) REFERENCES membership_grants(id,customer_id);
ALTER TABLE membership_orders ADD CONSTRAINT paid_fulfillment_complete CHECK (
 (status='PAID' AND paid_at IS NOT NULL AND grant_id IS NOT NULL)
 OR (status<>'PAID' AND paid_at IS NULL AND grant_id IS NULL));

CREATE FUNCTION protect_order_snapshot() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
 IF ROW(NEW.id,NEW.customer_id,NEW.plan_id,NEW.request_key,NEW.name,NEW.period,NEW.entitlement_mode,
        NEW.credits,NEW.validity_days,NEW.amount_minor,NEW.currency,NEW.plan_snapshot,NEW.simulated,NEW.created_at,NEW.expires_at)
    IS DISTINCT FROM
    ROW(OLD.id,OLD.customer_id,OLD.plan_id,OLD.request_key,OLD.name,OLD.period,OLD.entitlement_mode,
        OLD.credits,OLD.validity_days,OLD.amount_minor,OLD.currency,OLD.plan_snapshot,OLD.simulated,OLD.created_at,OLD.expires_at)
 THEN RAISE EXCEPTION 'order terms are immutable'; END IF;
 IF OLD.status='PAID' AND ROW(NEW.status,NEW.paid_at,NEW.grant_id) IS DISTINCT FROM ROW(OLD.status,OLD.paid_at,OLD.grant_id)
 THEN RAISE EXCEPTION 'paid fulfillment is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_order_terms BEFORE UPDATE ON membership_orders FOR EACH ROW EXECUTE FUNCTION protect_order_snapshot();
CREATE TRIGGER immutable_order_services BEFORE UPDATE OR DELETE ON order_services FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
