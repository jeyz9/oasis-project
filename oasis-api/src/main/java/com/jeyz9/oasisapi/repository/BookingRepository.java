package com.jeyz9.oasisapi.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

/** JPA persistence boundary for the booking domain. PostgreSQL locking/upserts stay explicit. */
@Repository @Transactional
public class BookingRepository extends JpaNativeQueries {
    static final String HISTORY = """
        SELECT b.*,v.name,u.display_name AS trainer_name,s.location,s.cancellation_minutes,s.state AS session_state
        FROM bookings b JOIN class_sessions s ON s.id=b.session_id
        JOIN services v ON v.id=s.service_id JOIN users u ON u.id=s.trainer_id
        """;

    public List<UUID> lockEligibleCustomer(UUID customerId) {
        return scalars("SELECT u.id FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.id=? AND u.enabled AND r.role='CUSTOMER' FOR SHARE OF u",UUID.class,customerId);
    }

    public Long countOwnedGrants(UUID grantId,UUID customerId) {
        return scalar("SELECT count(*) FROM membership_grants WHERE id=? AND customer_id=?",Long.class,grantId,customerId);
    }

    public Long countSessions(UUID sessionId) {
        return scalar("SELECT count(*) FROM class_sessions WHERE id=?",Long.class,sessionId);
    }

    public int insertBookingRequest(UUID customerId,String requestKey,UUID sessionId,UUID grantId) {
        return execute("INSERT INTO booking_requests(customer_id,request_key,session_id,grant_id) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",customerId,requestKey,sessionId,grantId);
    }

    public Map<String,Object> lockBookingRequest(UUID customerId,String requestKey) {
        return map("SELECT * FROM booking_requests WHERE customer_id=? AND request_key=? FOR UPDATE",customerId,requestKey);
    }

    public List<Boolean> lockActiveService(UUID serviceId) {
        return scalars("SELECT active FROM services WHERE id=? FOR SHARE",Boolean.class,serviceId);
    }

    public Long countConfirmedBookings(UUID customerId,UUID sessionId) {
        return scalar("SELECT count(*) FROM bookings WHERE customer_id=? AND session_id=? AND status='CONFIRMED'",Long.class,customerId,sessionId);
    }

    public Long countGrantEligibility(UUID grantId,UUID serviceId) {
        return scalar("SELECT count(*) FROM grant_services WHERE grant_id=? AND service_id=?",Long.class,grantId,serviceId);
    }

    public int debitAvailableCredit(UUID grantId) {
        return execute("UPDATE membership_grants SET remaining_credits=remaining_credits-1 WHERE id=? AND remaining_credits>0",grantId);
    }

    public int reserveAvailableSeat(UUID sessionId) {
        return execute("UPDATE class_sessions SET reserved_count=reserved_count+1 WHERE id=? AND reserved_count<capacity",sessionId);
    }

    public int insertConfirmedBooking(UUID bookingId,UUID customerId,UUID sessionId,UUID grantId,Instant bookedAt,Instant startsAt,Instant endsAt,int cost) {
        return execute("INSERT INTO bookings(id,customer_id,session_id,grant_id,status,booked_at,starts_at,ends_at,cost) VALUES (?,?,?,?,'CONFIRMED',?,?,?,?)",bookingId,customerId,sessionId,grantId,bookedAt,startsAt,endsAt,cost);
    }

    public int appendBookingDebit(UUID entryId,UUID grantId,UUID bookingId,Instant createdAt) {
        return execute("INSERT INTO credit_ledger(id,grant_id,booking_id,kind,delta,reason,created_at) VALUES (?,?,?,'DEBIT',-1,'Class confirmation',?)",entryId,grantId,bookingId,createdAt);
    }

    public int completeBookingRequest(UUID bookingId,UUID customerId,String requestKey) {
        return execute("UPDATE booking_requests SET booking_id=? WHERE customer_id=? AND request_key=?",bookingId,customerId,requestKey);
    }

    public <T> List<T> bookingHistory(ProjectionMapper<T> mapper,Object... parameters) {
        return rows(HISTORY+" WHERE b.customer_id=? ORDER BY b.booked_at DESC,b.id LIMIT ? OFFSET ?",mapper,parameters);
    }

    public <T> List<T> findOwnedBooking(ProjectionMapper<T> mapper,Object... parameters) {
        return rows(HISTORY+" WHERE b.id=? AND b.customer_id=?",mapper,parameters);
    }

    public List<UUID> findCancelBookingsSessionId(Object... parameters) {
        return scalars("SELECT session_id FROM bookings WHERE id=? AND customer_id=?",UUID.class,parameters);
    }

    public UUID findRestoreMembershipGrantsLocked(Object... parameters) {
        return scalar("SELECT id FROM membership_grants WHERE id=? FOR NO KEY UPDATE",UUID.class,parameters);
    }

    public UUID findRestoreCreditLedgerIdDebit(Object... parameters) {
        return scalar("SELECT id FROM credit_ledger WHERE booking_id=? AND kind='DEBIT'",UUID.class,parameters);
    }

    public int updateRestoreBookingsStatusCancelledAtCancelled(Object... parameters) {
        return execute("UPDATE bookings SET status='CANCELLED',cancelled_at=? WHERE id=?",parameters);
    }

    public int updateRestoreClassSessionsReservedCount(Object... parameters) {
        return execute("UPDATE class_sessions SET reserved_count=reserved_count-1 WHERE id=? AND reserved_count>0",parameters);
    }

    public int insertRestoreCreditLedgerRestore(Object... parameters) {
        return execute("INSERT INTO credit_ledger(id,grant_id,booking_id,kind,delta,reverses_id,reason,created_at) VALUES (?,?,?,'RESTORE',1,?,?,?)",parameters);
    }

    public int updateRestoreMembershipGrantsRemainingCredits(Object... parameters) {
        return execute("UPDATE membership_grants SET remaining_credits=remaining_credits+1 WHERE id=?",parameters);
    }

    public int updateCancelSessionClassSessionsStateCancelled(Object... parameters) {
        return execute("UPDATE class_sessions SET state='CANCELLED' WHERE id=?",parameters);
    }

    public List<UUID> findCancelSessionBookingsLockedConfirmed(Object... parameters) {
        return scalars("SELECT id FROM bookings WHERE session_id=? AND status='CONFIRMED' ORDER BY id FOR NO KEY UPDATE",UUID.class,parameters);
    }

    public List<UUID> findCancelSessionBookingsConfirmed(Object... parameters) {
        return scalars("SELECT DISTINCT grant_id FROM bookings WHERE session_id=? AND status='CONFIRMED' ORDER BY grant_id",UUID.class,parameters);
    }

    public UUID findCancelSessionMembershipGrantsLocked(Object... parameters) {
        return scalar("SELECT id FROM membership_grants WHERE id=? FOR NO KEY UPDATE",UUID.class,parameters);
    }

    public int insertCancelSessionAuditEventsCancelSession(Object... parameters) {
        return execute("INSERT INTO audit_events(id,actor_id,action,resource_id,reason) VALUES (?,?,'CANCEL_SESSION',?,?)",parameters);
    }

    public <T> List<T> findGrantsBookingsCountCancelled(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT g.*,(SELECT count(*) FROM bookings b WHERE b.grant_id=g.id AND b.status<>'CANCELLED') AS used_sessions FROM membership_grants g WHERE customer_id=? ORDER BY valid_until,id LIMIT ? OFFSET ?",mapper,parameters);
    }

    public <T> List<T> findLedgerCreditLedger(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT l.* FROM credit_ledger l JOIN membership_grants g ON g.id=l.grant_id WHERE g.customer_id=? ORDER BY l.created_at DESC,l.id LIMIT ? OFFSET ?",mapper,parameters);
    }

    public Long findAttendeesClassSessionsCount(Object... parameters) {
        return scalar("SELECT count(*) FROM class_sessions WHERE id=? AND trainer_id=?",Long.class,parameters);
    }

    public <T> List<T> findAttendeesBookingsIdDisplayNameCancelled(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT b.id,u.display_name,b.status FROM bookings b JOIN users u ON u.id=b.customer_id WHERE b.session_id=? AND b.status<>'CANCELLED' ORDER BY u.display_name,b.id LIMIT 100",mapper,parameters);
    }
}
