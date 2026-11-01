package com.jeyz9.oasisapi.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JPA persistence boundary for the training domain. PostgreSQL locking/upserts stay explicit. */
@Repository @Transactional
public class TrainingRepository extends JpaNativeQueries {
    public void saveAvailability(UUID id,UUID trainer,java.time.Instant startsAt,java.time.Instant endsAt) {
        var window=new com.jeyz9.oasisapi.models.TrainerAvailability();window.setId(id);window.setTrainerId(trainer);window.setStartsAt(startsAt);window.setEndsAt(endsAt);entityManager.persist(window);entityManager.flush();
    }
    public void saveReview(UUID id,UUID booking,UUID customer,int rating,String body,java.time.Instant now) {
        var review=new com.jeyz9.oasisapi.models.Review();review.setId(id);review.setBookingId(booking);review.setCustomerId(customer);review.setRating(rating);review.setBody(body);review.setCreatedAt(now);entityManager.persist(review);entityManager.flush();
    }

    public <T> List<T> findAvailabilityTrainerAvailability(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT * FROM trainer_availability WHERE trainer_id=? AND ends_at>? AND starts_at<? ORDER BY starts_at,id LIMIT 200",mapper,parameters);
    }

    public UUID findAddAvailabilityUsersLocked(Object... parameters) {
        return scalar("SELECT id FROM users WHERE id=? FOR UPDATE",UUID.class,parameters);
    }

    public UUID findRemoveAvailabilityUsersLocked(Object... parameters) {
        return scalar("SELECT id FROM users WHERE id=? FOR UPDATE",UUID.class,parameters);
    }

    public List<Map<String,Object>> findRemoveAvailabilityTrainerAvailabilityLocked(Object... parameters) {
        return maps("SELECT * FROM trainer_availability WHERE id=? AND trainer_id=? FOR UPDATE",parameters);
    }

    public Long findRemoveAvailabilityClassSessions(Object... parameters) {
        return scalar("""
            SELECT count(*) FROM class_sessions s JOIN services v ON s.service_id=v.id
            WHERE s.trainer_id=? AND v.kind='PRIVATE' AND s.state IN ('DRAFT','PUBLISHED') AND s.ends_at>?
            AND s.starts_at>=? AND s.ends_at<=?
            """,Long.class,parameters);
    }

    public int deleteRemoveAvailabilityTrainerAvailability(Object... parameters) {
        return execute("DELETE FROM trainer_availability WHERE id=?",parameters);
    }

    public List<Map<String,Object>> findAssignedSessionClassSessionsLocked(Object... parameters) {
        return maps("SELECT * FROM class_sessions WHERE id=? AND trainer_id=? FOR UPDATE",parameters);
    }

    public List<UUID> findRecordBookingsSessionId(Object... parameters) {
        return scalars("SELECT session_id FROM bookings WHERE id=?",UUID.class,parameters);
    }

    public String findRecordBookingsLocked(Object... parameters) {
        return scalar("SELECT status FROM bookings WHERE id=? FOR UPDATE",String.class,parameters);
    }

    public int updateRecordBookingsStatus(Object... parameters) {
        return execute("UPDATE bookings SET status=? WHERE id=?",parameters);
    }

    public int writeRecordAttendanceRecords(Object... parameters) {
        return execute("""
            INSERT INTO attendance_records VALUES (?,?,?,?) ON CONFLICT (booking_id)
            DO UPDATE SET recorded_by=excluded.recorded_by,outcome=excluded.outcome,recorded_at=excluded.recorded_at
            """,parameters);
    }

    public String findRecordBookingsDisplayName(Object... parameters) {
        return scalar("SELECT u.display_name FROM bookings b JOIN users u ON b.customer_id=u.id WHERE b.id=?",String.class,parameters);
    }

    public Long findOutcomesClassSessionsCount(Object... parameters) {
        return scalar("SELECT count(*) FROM class_sessions WHERE id=? AND trainer_id=?",Long.class,parameters);
    }

    public <T> List<T> findOutcomesBookingsIdDisplayName(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT b.id,u.display_name,b.status,a.outcome,a.recorded_at FROM bookings b JOIN attendance_records a ON b.id=a.booking_id JOIN users u ON b.customer_id=u.id WHERE b.session_id=? ORDER BY b.id LIMIT 100",mapper,parameters);
    }

    public Long findCompleteBookingsCountConfirmed(Object... parameters) {
        return scalar("SELECT count(*) FROM bookings WHERE session_id=? AND status='CONFIRMED'",Long.class,parameters);
    }

    public int updateCompleteClassSessionsStateCompleted(Object... parameters) {
        return execute("UPDATE class_sessions SET state='COMPLETED' WHERE id=?",parameters);
    }

    public List<UUID> findReviewBookingsSessionId(Object... parameters) {
        return scalars("SELECT session_id FROM bookings WHERE id=? AND customer_id=?",UUID.class,parameters);
    }

    public String findReviewClassSessionsLocked(Object... parameters) {
        return scalar("SELECT state FROM class_sessions WHERE id=? FOR SHARE",String.class,parameters);
    }

    public String findReviewBookingsLocked(Object... parameters) {
        return scalar("SELECT status FROM bookings WHERE id=? FOR UPDATE",String.class,parameters);
    }

    public <T> List<T> findReviewsReviews(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT * FROM reviews WHERE customer_id=? ORDER BY created_at DESC,id LIMIT 100",mapper,parameters);
    }

    public int insertAuditAuditEvents(Object... parameters) {
        return execute("INSERT INTO audit_events(id,actor_id,action,resource_id,reason) VALUES (?,?,?,?,?)",parameters);
    }
}
