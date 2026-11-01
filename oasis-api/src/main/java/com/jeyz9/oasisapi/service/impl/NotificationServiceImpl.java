package com.jeyz9.oasisapi.service.impl;

import com.jeyz9.oasisapi.component.NotificationTransport;
import com.jeyz9.oasisapi.service.NotificationService;
import com.jeyz9.oasisapi.repository.NotificationRepository;
import com.jeyz9.oasisapi.dto.NotificationDto.Inbox;
import com.jeyz9.oasisapi.dto.NotificationDto.PreferenceInput;
import com.jeyz9.oasisapi.dto.NotificationDto.Preferences;
import com.jeyz9.oasisapi.dto.NotificationDto.Retry;
import com.jeyz9.oasisapi.utils.Actor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service @Validated
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repository;private final Clock clock;private final NotificationTransport transport;private final TransactionTemplate tx;
    public NotificationServiceImpl(NotificationRepository repository,Clock clock,NotificationTransport transport,PlatformTransactionManager manager){this.repository=repository;this.clock=clock;this.transport=transport;tx=new TransactionTemplate(manager);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void confirmed(UUID booking) {
        var data=data(booking);String when=DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm",Locale.ENGLISH).withZone(ZoneId.of("Asia/Bangkok")).format((Instant)data.get("starts_at"));
        enqueue(data,booking,"BOOKING_CONFIRMED","Booking confirmed",data.get("name")+" · "+when+" Bangkok · "+data.get("location"),clock.instant());
        Instant reminder=((Instant)data.get("starts_at")).minus(Duration.ofHours(1));
        if(reminder.isAfter(clock.instant())) enqueue(data,booking,"CLASS_REMINDER","Your class starts in one hour",data.get("name")+" · "+when+" Bangkok · "+data.get("location"),reminder);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void cancelled(UUID booking) {
        repository.cancelPendingBookingJobs(booking);
        var data=data(booking);
        enqueue(data,booking,"BOOKING_CANCELLED","Booking cancelled","Your "+data.get("name")+" booking was cancelled. Check your schedule and membership for current details.",clock.instant());
    }
    private Map<String,Object> data(UUID booking) {return repository.findDataBookingsCustomerIdStartsAt(booking);}
    private void enqueue(Map<String,Object> data,UUID booking,String kind,String title,String body,Instant due) {
        UUID owner=(UUID)data.get("customer_id");var pref=repository.findEnqueueNotificationPreferences(owner);
        var channels=new ArrayList<>(List.of("INAPP"));
        if(!pref.isEmpty()){if((Boolean)pref.getFirst().get("email_enabled"))channels.add("EMAIL");if((Boolean)pref.getFirst().get("line_enabled"))channels.add("LINE");}
        for(var channel:channels) repository.insertEnqueueNotificationJobsPending(UUID.randomUUID(),owner,booking,kind,channel,booking+":"+kind+":"+channel,title,body,due,clock.instant());
    }
    public int drain() {
        int handled=0;
        for(int i=0;i<20;i++) {
            var job=tx.execute(status->{
                var rows=repository.findDrainNotificationJobsLockedPendingRetryProcessing(clock.instant(),clock.instant());
                if(rows.isEmpty())return null;
                var row=rows.getFirst();UUID token=UUID.randomUUID();
                repository.updateDrainNotificationJobsStateAttemptsLeaseTokenProcessing(token,clock.instant().plusSeconds(60),row.get("id"));
                row.put("lease_token",token);row.put("attempts",((Number)row.get("attempts")).intValue()+1);return row;
            });
            if(job==null)break;
            deliver(job);handled++;
        }
        return handled;
    }
    private String cancelledCode(Map<String,Object> job) {
        if(repository.findCancelledCodeUsersCount(job.get("owner_id"))!=1)return "ACCOUNT_DISABLED";
        if(!job.get("kind").equals("BOOKING_CANCELLED")) {
            var b=repository.findCancelledCodeBookingsStatusState(job.get("booking_id"));
            if(!b.get("status").equals("CONFIRMED") || !b.get("state").equals("PUBLISHED"))return "BOOKING_NOT_ACTIVE";
            if(job.get("kind").equals("CLASS_REMINDER") && !((Instant)b.get("starts_at")).isAfter(clock.instant()))return "SESSION_ALREADY_STARTED";
        }
        return null;
    }
    private void deliver(Map<String,Object> job) {
        UUID id=(UUID)job.get("id"),token=(UUID)job.get("lease_token");
        try {
            String cancelled=cancelledCode(job);if(cancelled!=null){finish(job,"CANCELLED",false,cancelled);return;}
            String channel=(String)job.get("channel");
            if(channel.equals("INAPP")) {
                tx.executeWithoutResult(status->{
                    var owned=repository.findDeliverNotificationJobsLockedProcessing(id,token);
                    if(owned.isEmpty())return;
                    String stale=cancelledCode(job);if(stale!=null){finish(job,"CANCELLED",false,stale);return;}
                    repository.insertDeliverNotifications(id,job.get("owner_id"),job.get("title"),job.get("body"),clock.instant());
                    finish(job,"DELIVERED",false,"INBOX_DELIVERED");
                });return;
            }
            var prefs=repository.findDeliverNotificationPreferences(job.get("owner_id"));
            if(prefs.isEmpty() || !(Boolean)prefs.getFirst().get(channel.equals("EMAIL")?"email_enabled":"line_enabled")){finish(job,"CANCELLED",false,"OPTED_OUT");return;}
            String destination=channel.equals("EMAIL")?repository.findDeliverUsersEmail(job.get("owner_id")):(String)prefs.getFirst().get("line_destination");
            if(destination==null){finish(job,"BLOCKED",false,"LINE_NOT_LINKED");return;}
            var result=transport.send(id,channel,destination,(String)job.get("title"),(String)job.get("body"));
            if(!Set.of("DELIVERED","BLOCKED","RETRY").contains(result.state()))throw new IllegalStateException("Invalid delivery result");
            finish(job,result.state(),result.simulated(),result.code());
        } catch(RuntimeException error){finish(job,"RETRY",false,"DELIVERY_FAILED");}
    }
    private void finish(Map<String,Object> job,String state,boolean simulated,String code) {
        int attempts=((Number)job.get("attempts")).intValue();
        if(state.equals("RETRY") && attempts>=5)state="DEAD";
        long delay=Math.min(3600,30L << Math.min(attempts-1,6));
        repository.updateFinishNotificationJobsStateSimulatedLastCodeProcessing(state,simulated,code,clock.instant().plusSeconds(delay),state.equals("DELIVERED")?clock.instant():null,job.get("id"),job.get("lease_token"));
    }
    public List<Inbox> inbox(int offset,int limit) {
        return repository.inbox(Actor.id(),offset,limit);
    }
    public void read(UUID id) {if(repository.markRead(id,Actor.id(),clock.instant())!=1)throw fail(404,"NOTIFICATION_NOT_FOUND");}
    public Preferences preferences() {
        var rows=repository.findPreferencesNotificationPreferences(Actor.id());if(rows.isEmpty())return new Preferences(false,false,false);
        var p=rows.getFirst();return new Preferences((Boolean)p.get("email_enabled"),(Boolean)p.get("line_enabled"),p.get("line_destination")!=null);
    }
    public Preferences preferences(PreferenceInput input) {
        repository.insertPreferencesNotificationPreferences(Actor.id(),input.emailEnabled(),input.lineEnabled());return preferences();
    }
    @PreAuthorize("hasRole('ADMIN')") public List<Map<String,Object>> jobs() {return repository.findJobsNotificationJobsIdKind();}
    @PreAuthorize("hasRole('ADMIN')") @Transactional public void retry(UUID id,Retry input) {
        if(repository.resetRetryableJob(clock.instant(),id)!=1)throw fail(409,"JOB_NOT_RETRYABLE");
        repository.insertRetryAuditEventsRetryNotification(UUID.randomUUID(),Actor.id(),id,input.reason().strip());
    }
    @PreAuthorize("hasRole('ADMIN')") public Map<String,Integer> manualRun(){return Map.of("processed",drain());}
}
