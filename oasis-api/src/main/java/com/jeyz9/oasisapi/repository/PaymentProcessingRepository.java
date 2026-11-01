package com.jeyz9.oasisapi.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JPA persistence boundary for the paymentprocessing domain. PostgreSQL locking/upserts stay explicit. */
@Repository @Transactional
public class PaymentProcessingRepository extends JpaNativeQueries {

    public List<Map<String,Object>> findProcessMembershipOrdersId(Object... parameters) {
        return maps("SELECT id FROM membership_orders WHERE id=?",parameters);
    }

    public int writeProcessPaymentEvents(Object... parameters) {
        return execute("""
            INSERT INTO payment_events VALUES (?,?,?,?,?,?,?,'PROCESSED','RECEIVED',?)
            ON CONFLICT(provider,merchant_id,external_event_id) DO NOTHING
            """,parameters);
    }

    public Map<String,Object> findProcessPaymentEventsLocked(Object... parameters) {
        return map("SELECT * FROM payment_events WHERE provider=? AND merchant_id=? AND external_event_id=? FOR UPDATE",parameters);
    }

    public Map<String,Object> findProcessMembershipOrdersLocked(Object... parameters) {
        return map("SELECT * FROM membership_orders WHERE id=? FOR NO KEY UPDATE",parameters);
    }

    public int updateProcessPaymentEventsStateCode(Object... parameters) {
        return execute("UPDATE payment_events SET state=?,code=? WHERE id=?",parameters);
    }

    public List<Map<String,Object>> findPaymentPaymentsLocked(Object... parameters) {
        return maps("SELECT * FROM payments WHERE provider=? AND merchant_id=? AND external_id=? FOR NO KEY UPDATE",parameters);
    }

    public int writePaymentPayments(Object... parameters) {
        return execute("""
                INSERT INTO payments(id,order_id,provider,merchant_id,external_id,amount_minor,currency,status,disposition,simulated)
                VALUES (?,?,?,?,?,?,?,'FAILED','NONE',?) ON CONFLICT(provider,merchant_id,external_id) DO NOTHING
                """,parameters);
    }

    public Map<String,Object> findPaymentPaymentsLockedRow(Object... parameters) {
        return map("SELECT * FROM payments WHERE provider=? AND merchant_id=? AND external_id=? FOR NO KEY UPDATE",parameters);
    }

    public Long findPaymentUsersCountCustomer(Object... parameters) {
        return scalar("SELECT count(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.id=? AND u.enabled AND r.role='CUSTOMER'",Long.class,parameters);
    }

    public int updatePaymentPaymentsStatusDispositionCapturedAtSucceededReview(Object... parameters) {
        return execute("UPDATE payments SET status='SUCCEEDED',disposition='REVIEW',captured_at=? WHERE id=?",parameters);
    }

    public int updatePaymentMembershipOrdersStatusReview(Object... parameters) {
        return execute("UPDATE membership_orders SET status='REVIEW' WHERE id=?",parameters);
    }

    public int updatePaymentPaymentsStatusDispositionCapturedAtSucceededAccepted(Object... parameters) {
        return execute("UPDATE payments SET status='SUCCEEDED',disposition='ACCEPTED',captured_at=? WHERE id=?",parameters);
    }

    public int updatePaymentMembershipOrdersStatusPaidAtGrantIdPaid(Object... parameters) {
        return execute("UPDATE membership_orders SET status='PAID',paid_at=?,grant_id=? WHERE id=?",parameters);
    }

    public int writePaymentReceipts(Object... parameters) {
        return execute("""
            INSERT INTO receipts SELECT ?,?,?,?,u.id,u.display_name,?, ?,?,?,?
            FROM users u WHERE u.id=?
            """,parameters);
    }

    public int writeFulfillMembershipGrants(Object... parameters) {
        return execute("""
            INSERT INTO membership_grants(id,customer_id,name,source,valid_from,valid_until,status,remaining_credits,entitlement_mode,plan_id,plan_snapshot)
            SELECT ?,customer_id,name,?, ?,?,'ACTIVE',credits,entitlement_mode,plan_id,plan_snapshot FROM membership_orders WHERE id=?
            """,parameters);
    }

    public int insertFulfillGrantServices(Object... parameters) {
        return execute("INSERT INTO grant_services SELECT ?,service_id FROM order_services WHERE order_id=?",parameters);
    }

    public int insertFulfillCreditLedgerGrant(Object... parameters) {
        return execute("INSERT INTO credit_ledger(id,grant_id,kind,delta,reason,created_at) VALUES (?,?,'GRANT',?,?,?)",parameters);
    }

    public List<Map<String,Object>> findRefundRefunds(Object... parameters) {
        return maps("""
            SELECT f.*,p.external_id AS payment_reference FROM refunds f JOIN payments p ON f.payment_id=p.id
            WHERE f.id=? AND p.order_id=? AND p.provider=? AND p.merchant_id=? AND p.disposition='ACCEPTED' FOR NO KEY UPDATE OF f
            """,parameters);
    }

    public UUID findRefundMembershipGrantsLocked(Object... parameters) {
        return scalar("SELECT id FROM membership_grants WHERE id=? FOR NO KEY UPDATE",UUID.class,parameters);
    }

    public int updateRefundRefundsStateExternalIdConfirmedAt(Object... parameters) {
        return execute("UPDATE refunds SET state=?,external_id=?,confirmed_at=? WHERE id=?",parameters);
    }

    public int updateRefundMembershipGrantsStatusRevoked(Object... parameters) {
        return execute("UPDATE membership_grants SET status=? WHERE id=? AND status<>'REVOKED'",parameters);
    }

    public <T> List<T> findEventsPaymentEvents(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT * FROM payment_events ORDER BY received_at DESC,id LIMIT 200",mapper,parameters);
    }
}
