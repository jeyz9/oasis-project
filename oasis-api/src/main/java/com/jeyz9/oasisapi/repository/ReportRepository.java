package com.jeyz9.oasisapi.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JPA persistence boundary for the report domain. PostgreSQL locking/upserts stay explicit. */
@Repository @Transactional
public class ReportRepository extends JpaNativeQueries {

    public <T> List<T> completedTraining(Instant from,Instant to,UUID trainer,ProjectionMapper<T> mapper) {
        String select="""
            SELECT s.trainer_id,u.display_name,count(*) AS taught,sum(s.capacity) AS capacity,
            sum((SELECT count(*) FROM bookings b WHERE b.session_id=s.id AND b.status='ATTENDED')) AS attended,
            sum((SELECT count(*) FROM bookings b WHERE b.session_id=s.id AND b.status='NO_SHOW')) AS no_shows,
            sum(s.commission_minor) AS commission
            FROM class_sessions s JOIN users u ON s.trainer_id=u.id
            WHERE s.state='COMPLETED' AND s.starts_at>=? AND s.starts_at<?
            """;
        var args=new ArrayList<Object>(List.of(from,to));
        if(trainer!=null){select+=" AND s.trainer_id=?";args.add(trainer);}
        select+=" GROUP BY s.trainer_id,u.display_name ORDER BY u.display_name,s.trainer_id";
        return rows(select,mapper,args.toArray());
    }
    public <T> List<T> cashFlowBuckets(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("""
            WITH flows AS (
             SELECT captured_at AS at,amount_minor AS gross,0::bigint AS refund,1::bigint AS packages FROM payments
              WHERE status='SUCCEEDED' AND disposition='ACCEPTED' AND simulated=?
             UNION ALL
             SELECT f.confirmed_at,0::bigint,f.amount_minor,0::bigint FROM refunds f JOIN payments p ON f.payment_id=p.id
              WHERE f.state='SUCCEEDED' AND p.disposition='ACCEPTED' AND p.simulated=?
            ) SELECT date_trunc(cast(? as text),at AT TIME ZONE 'Asia/Bangkok')::date AS bucket,
              sum(packages) AS packages,sum(gross) AS gross,sum(refund) AS refunds FROM flows
              WHERE at>=? AND at<? GROUP BY bucket ORDER BY bucket
            """,mapper,parameters);
    }

    public Long unreconciledCaptureAmount(Object... parameters) {
        return scalar("SELECT coalesce(sum(amount_minor),0) FROM payments WHERE status='SUCCEEDED' AND disposition='REVIEW' AND simulated=? AND captured_at>=? AND captured_at<?",Long.class,parameters);
    }

    public Long creditMismatchCount(Object... parameters) {
        return scalar("SELECT count(*) FROM membership_grants g WHERE entitlement_mode='CREDITS' AND remaining_credits<>(SELECT coalesce(sum(delta),0) FROM credit_ledger l WHERE l.grant_id=g.id)",Long.class,parameters);
    }

    public Long seatMismatchCount(Object... parameters) {
        return scalar("SELECT count(*) FROM class_sessions s WHERE reserved_count<>(SELECT count(*) FROM bookings b WHERE b.session_id=s.id AND b.status<>'CANCELLED')",Long.class,parameters);
    }

    public Long blockedNotificationCount(Object... parameters) {
        return scalar("SELECT count(*) FROM notification_jobs WHERE state='BLOCKED'",Long.class,parameters);
    }

    public Long deadNotificationCount(Object... parameters) {
        return scalar("SELECT count(*) FROM notification_jobs WHERE state='DEAD'",Long.class,parameters);
    }

    public Long pendingNotificationCount(Object... parameters) {
        return scalar("SELECT count(*) FROM notification_jobs WHERE state IN ('PENDING','RETRY','PROCESSING')",Long.class,parameters);
    }

    public Long paymentReviewCount(Object... parameters) {
        return scalar("SELECT count(*) FROM payments WHERE disposition='REVIEW'",Long.class,parameters);
    }

    public Integer healthProbe(Object... parameters) {
        return scalar("SELECT 1",Integer.class,parameters);
    }
}
