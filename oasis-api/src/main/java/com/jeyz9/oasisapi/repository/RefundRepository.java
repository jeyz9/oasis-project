package com.jeyz9.oasisapi.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JPA persistence boundary for the refund domain. PostgreSQL locking/upserts stay explicit. */
@Repository @Transactional
public class RefundRepository extends JpaNativeQueries {

    public List<Map<String,Object>> findRequestRefunds(Object... parameters) {
        return maps("SELECT * FROM refunds WHERE actor_id=? AND request_key=?",parameters);
    }

    public List<UUID> findRequestPaymentsOrderIdAccepted(Object... parameters) {
        return scalars("SELECT order_id FROM payments WHERE id=? AND disposition='ACCEPTED'",UUID.class,parameters);
    }

    public Map<String,Object> findRequestMembershipOrdersLocked(Object... parameters) {
        return map("SELECT * FROM membership_orders WHERE id=? FOR NO KEY UPDATE",parameters);
    }

    public UUID findRequestPaymentsLocked(Object... parameters) {
        return scalar("SELECT id FROM payments WHERE id=? FOR NO KEY UPDATE",UUID.class,parameters);
    }

    public List<Map<String,Object>> findRequestRefundsRows(Object... parameters) {
        return maps("SELECT * FROM refunds WHERE payment_id=?",parameters);
    }

    public Map<String,Object> findRequestMembershipGrantsLocked(Object... parameters) {
        return map("SELECT * FROM membership_grants WHERE id=? FOR NO KEY UPDATE",parameters);
    }

    public Long findRequestBookingsCountCancelled(Object... parameters) {
        return scalar("SELECT count(*) FROM bookings WHERE grant_id=? AND status<>'CANCELLED'",Long.class,parameters);
    }

    public int insertRequestRefundsPending(Object... parameters) {
        return execute("INSERT INTO refunds(id,payment_id,actor_id,request_key,reason,amount_minor,state,created_at) VALUES (?,?,?,?,?,?,'PENDING',?)",parameters);
    }

    public int updateRequestMembershipGrantsStatusSuspended(Object... parameters) {
        return execute("UPDATE membership_grants SET status='SUSPENDED' WHERE id=?",parameters);
    }

    public int insertRequestAuditEventsRequestFullRefund(Object... parameters) {
        return execute("INSERT INTO audit_events(id,actor_id,action,resource_id,reason) VALUES (?,?,'REQUEST_FULL_REFUND',?,?)",parameters);
    }

    public List<Map<String,Object>> findDispatchRefunds(Object... parameters) {
        return maps("SELECT f.*,p.external_id AS payment_reference,p.currency,p.simulated,p.provider FROM refunds f JOIN payments p ON f.payment_id=p.id WHERE f.id=?",parameters);
    }

    public Map<String,Object> findDispatchRefundsLocked(Object... parameters) {
        return map("SELECT external_id FROM refunds WHERE id=? FOR NO KEY UPDATE",parameters);
    }

    public int updateDispatchRefundsExternalId(Object... parameters) {
        return execute("UPDATE refunds SET external_id=? WHERE id=?",parameters);
    }

    public List<Map<String,Object>> findAllRefunds(Object... parameters) {
        return maps("SELECT * FROM refunds ORDER BY created_at DESC,id LIMIT 200",parameters);
    }
}
