package com.jeyz9.oasisapi.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

/** JPA persistence boundary for the order domain. PostgreSQL locking/upserts stay explicit. */
@Repository @Transactional
public class OrderRepository extends JpaNativeQueries {
    static final String SELECT="""
        SELECT o.*,u.display_name AS customer_name,r.id AS receipt_id,p.id AS payment_id,f.state AS refund_status FROM membership_orders o
        JOIN users u ON u.id=o.customer_id
        LEFT JOIN receipts r ON r.order_id=o.id
        LEFT JOIN payments p ON p.order_id=o.id AND p.disposition='ACCEPTED'
        LEFT JOIN refunds f ON f.payment_id=p.id
        """;
    public List<Map<String,Object>> findOwnRows(Object... parameters) {
        return maps(SELECT+" WHERE o.id=? AND o.customer_id=?",parameters);
    }

    public List<Map<String,Object>> findHistoryRows(Object... parameters) {
        return maps(SELECT+" WHERE o.customer_id=? ORDER BY o.created_at DESC,o.id LIMIT ? OFFSET ?",parameters);
    }

    public List<Map<String,Object>> findAllRows(Object... parameters) {
        return maps(SELECT+" ORDER BY o.created_at DESC,o.id LIMIT 200",parameters);
    }

    public List<Map<String,Object>> findCreateMembershipOrdersIdPlanId(Object... parameters) {
        return maps("SELECT id,plan_id FROM membership_orders WHERE customer_id=? AND request_key=?",parameters);
    }

    public List<Map<String,Object>> findCreateMembershipPlansLocked(Object... parameters) {
        return maps("SELECT * FROM membership_plans WHERE id=? FOR SHARE",parameters);
    }

    public int writeCreateMembershipOrders(Object... parameters) {
        return execute("""
            INSERT INTO membership_orders(id,customer_id,plan_id,request_key,name,period,entitlement_mode,credits,validity_days,
            amount_minor,currency,plan_snapshot,status,simulated,created_at,expires_at)
            SELECT ?,?,id,?,name,period,entitlement_mode,credits,validity_days,price_minor,currency,
            jsonb_build_object('name',name,'period',period,'entitlementMode',entitlement_mode,'credits',credits,'validityDays',validity_days,'priceMinor',price_minor,'currency',currency),
            'PENDING',?,?,? FROM membership_plans WHERE id=? ON CONFLICT(customer_id,request_key) DO NOTHING
            """,parameters);
    }

    public int insertCreateOrderServices(Object... parameters) {
        return execute("INSERT INTO order_services SELECT ?,service_id FROM plan_services WHERE plan_id=?",parameters);
    }

    public Map<String,Object> findCreateMembershipOrdersIdPlanIdRow(Object... parameters) {
        return map("SELECT id,plan_id FROM membership_orders WHERE customer_id=? AND request_key=?",parameters);
    }

    public List<Map<String,Object>> findCancelMembershipOrdersLocked(Object... parameters) {
        return maps("SELECT status FROM membership_orders WHERE id=? AND customer_id=? FOR NO KEY UPDATE",parameters);
    }

    public int updateCancelMembershipOrdersStatusCancelled(Object... parameters) {
        return execute("UPDATE membership_orders SET status='CANCELLED' WHERE id=?",parameters);
    }

    public List<Map<String,Object>> findReceiptReceipts(Object... parameters) {
        return maps("SELECT r.*,p.external_id FROM receipts r JOIN payments p ON p.id=r.payment_id WHERE r.id=? AND r.customer_id=?",parameters);
    }
}
