package com.jeyz9.oasisapi.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JPA persistence boundary for the notification domain. PostgreSQL locking/upserts stay explicit. */
@Repository @Transactional
public class NotificationRepository extends JpaNativeQueries {
    @Transactional(readOnly=true)
    public java.util.List<com.jeyz9.oasisapi.dto.NotificationDto.Inbox> inbox(UUID owner,int offset,int limit) {
        return entityManager.createQuery("select n from Notification n where n.ownerId=:owner order by n.createdAt desc,n.id",com.jeyz9.oasisapi.models.Notification.class).setParameter("owner",owner).setFirstResult(offset).setMaxResults(limit).getResultStream().map(n->new com.jeyz9.oasisapi.dto.NotificationDto.Inbox(n.getId(),n.getTitle(),n.getBody(),n.getCreatedAt(),n.getReadAt())).toList();
    }
    public int markRead(UUID id,UUID owner,java.time.Instant now) {
        entityManager.flush();
        int changed=entityManager.createQuery("update Notification n set n.readAt=coalesce(n.readAt,:now) where n.id=:id and n.ownerId=:owner").setParameter("now",now).setParameter("id",id).setParameter("owner",owner).executeUpdate();
        entityManager.clear();return changed;
    }

    public int cancelPendingBookingJobs(Object... parameters) {
        return execute("UPDATE notification_jobs SET state='CANCELLED',lease_token=NULL,lease_until=NULL,last_code='BOOKING_CANCELLED' WHERE booking_id=? AND kind IN ('CLASS_REMINDER','BOOKING_CONFIRMED') AND state<>'DELIVERED'",parameters);
    }

    public Map<String,Object> findDataBookingsCustomerIdStartsAt(Object... parameters) {
        return map("SELECT b.customer_id,s.starts_at,s.location,v.name FROM bookings b JOIN class_sessions s ON b.session_id=s.id JOIN services v ON s.service_id=v.id WHERE b.id=?",parameters);
    }

    public List<Map<String,Object>> findEnqueueNotificationPreferences(Object... parameters) {
        return maps("SELECT * FROM notification_preferences WHERE user_id=?",parameters);
    }

    public int insertEnqueueNotificationJobsPending(Object... parameters) {
        return execute("INSERT INTO notification_jobs(id,owner_id,booking_id,kind,channel,dedupe_key,title,body,state,next_attempt_at,created_at) VALUES (?,?,?,?,?,?,?,?,'PENDING',?,?) ON CONFLICT(dedupe_key) DO NOTHING",parameters);
    }

    public List<Map<String,Object>> findDrainNotificationJobsLockedPendingRetryProcessing(Object... parameters) {
        return maps("SELECT * FROM notification_jobs WHERE ((state IN ('PENDING','RETRY') AND next_attempt_at<=?) OR (state='PROCESSING' AND lease_until<=?)) ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED LIMIT 1",parameters);
    }

    public int updateDrainNotificationJobsStateAttemptsLeaseTokenProcessing(Object... parameters) {
        return execute("UPDATE notification_jobs SET state='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=? WHERE id=?",parameters);
    }

    public Long findCancelledCodeUsersCount(Object... parameters) {
        return scalar("SELECT count(*) FROM users WHERE id=? AND enabled",Long.class,parameters);
    }

    public Map<String,Object> findCancelledCodeBookingsStatusState(Object... parameters) {
        return map("SELECT b.status,s.state,s.starts_at FROM bookings b JOIN class_sessions s ON s.id=b.session_id WHERE b.id=?",parameters);
    }

    public List<Map<String,Object>> findDeliverNotificationJobsLockedProcessing(Object... parameters) {
        return maps("SELECT id FROM notification_jobs WHERE id=? AND lease_token=? AND state='PROCESSING' FOR UPDATE",parameters);
    }

    public int insertDeliverNotifications(Object... parameters) {
        return execute("INSERT INTO notifications(id,owner_id,title,body,created_at) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",parameters);
    }

    public List<Map<String,Object>> findDeliverNotificationPreferences(Object... parameters) {
        return maps("SELECT * FROM notification_preferences WHERE user_id=?",parameters);
    }

    public String findDeliverUsersEmail(Object... parameters) {
        return scalar("SELECT email FROM users WHERE id=?",String.class,parameters);
    }

    public int updateFinishNotificationJobsStateSimulatedLastCodeProcessing(Object... parameters) {
        return execute("UPDATE notification_jobs SET state=?,simulated=?,last_code=?,next_attempt_at=?,delivered_at=?,lease_token=NULL,lease_until=NULL WHERE id=? AND lease_token=? AND state='PROCESSING'",parameters);
    }

    public List<Map<String,Object>> findPreferencesNotificationPreferences(Object... parameters) {
        return maps("SELECT * FROM notification_preferences WHERE user_id=?",parameters);
    }

    public int insertPreferencesNotificationPreferences(Object... parameters) {
        return execute("INSERT INTO notification_preferences(user_id,email_enabled,line_enabled) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET email_enabled=excluded.email_enabled,line_enabled=excluded.line_enabled",parameters);
    }

    public List<Map<String,Object>> findJobsNotificationJobsIdKind(Object... parameters) {
        return maps("SELECT j.id,j.kind,j.channel,j.state,j.attempts,j.last_code,j.simulated,j.next_attempt_at,j.created_at,u.display_name AS recipient FROM notification_jobs j JOIN users u ON j.owner_id=u.id ORDER BY j.created_at DESC,j.id LIMIT 200",parameters);
    }

    public int resetRetryableJob(Object... parameters) {
        return execute("UPDATE notification_jobs SET state='PENDING',attempts=0,next_attempt_at=?,lease_until=NULL,lease_token=NULL WHERE id=? AND state IN ('BLOCKED','DEAD','RETRY')",parameters);
    }

    public int insertRetryAuditEventsRetryNotification(Object... parameters) {
        return execute("INSERT INTO audit_events(id,actor_id,action,resource_id,reason) VALUES (?,?,'RETRY_NOTIFICATION',?,?)",parameters);
    }
}
