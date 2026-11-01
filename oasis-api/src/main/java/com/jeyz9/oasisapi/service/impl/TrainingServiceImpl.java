package com.jeyz9.oasisapi.service.impl;

import com.jeyz9.oasisapi.service.TrainingService;
import com.jeyz9.oasisapi.repository.TrainingRepository;
import com.jeyz9.oasisapi.dto.TrainingDto.Attendance;
import com.jeyz9.oasisapi.dto.TrainingDto.Availability;
import com.jeyz9.oasisapi.dto.TrainingDto.AvailabilityInput;
import com.jeyz9.oasisapi.dto.TrainingDto.Outcome;
import com.jeyz9.oasisapi.dto.TrainingDto.Review;
import com.jeyz9.oasisapi.dto.TrainingDto.ReviewInput;
import com.jeyz9.oasisapi.utils.Actor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service @Validated
public class TrainingServiceImpl implements TrainingService {
    private final TrainingRepository repository;
    private final Clock clock;
    public TrainingServiceImpl(TrainingRepository repository,Clock clock) { this.repository=repository; this.clock=clock; }

    @PreAuthorize("hasRole('TRAINER')")
    public List<Availability> availability(LocalDate from,LocalDate to) {
        if(from==null || to==null || !to.isAfter(from) || to.isAfter(from.plusDays(31))) throw fail(400,"INVALID_DATE_RANGE");
        var zone=ZoneId.of("Asia/Bangkok");
        return repository.findAvailabilityTrainerAvailability((r,n)->new Availability(r.getObject("id",UUID.class),r.getInstant("starts_at"),r.getInstant("ends_at")),Actor.id(),from.atStartOfDay(zone).toInstant(),to.atStartOfDay(zone).toInstant());
    }
    @PreAuthorize("hasRole('TRAINER')") @Transactional
    public Availability addAvailability(AvailabilityInput input) {
        if(!input.startsAt().isAfter(clock.instant()) || !input.endsAt().isAfter(input.startsAt()) || input.endsAt().isAfter(input.startsAt().plus(Duration.ofDays(31))))
            throw fail(400,"INVALID_AVAILABILITY");
        UUID trainer=Actor.id();
        // Session creation and availability changes serialize on the trainer row.
        repository.findAddAvailabilityUsersLocked(trainer);
        UUID id=UUID.randomUUID();
        repository.saveAvailability(id,trainer,input.startsAt(),input.endsAt());
        return new Availability(id,input.startsAt(),input.endsAt());
    }
    @PreAuthorize("hasRole('TRAINER')") @Transactional
    public void removeAvailability(UUID id) {
        UUID trainer=Actor.id();
        repository.findRemoveAvailabilityUsersLocked(trainer);
        var rows=repository.findRemoveAvailabilityTrainerAvailabilityLocked(id,trainer);
        if(rows.isEmpty()) throw fail(404,"AVAILABILITY_NOT_FOUND");
        var row=rows.getFirst();
        long assigned=repository.findRemoveAvailabilityClassSessions(trainer,clock.instant(),row.get("starts_at"),row.get("ends_at"));
        if(assigned>0) throw fail(409,"AVAILABILITY_HAS_SESSIONS");
        repository.deleteRemoveAvailabilityTrainerAvailability(id);
    }
    private Map<String,Object> assignedSession(UUID id) {
        var rows=repository.findAssignedSessionClassSessionsLocked(id,Actor.id());
        if(rows.isEmpty()) throw fail(404,"SESSION_NOT_FOUND");
        return rows.getFirst();
    }
    @PreAuthorize("hasRole('TRAINER')") @Transactional
    public Outcome record(UUID booking,Attendance input) {
        var ids=repository.findRecordBookingsSessionId(booking);
        if(ids.isEmpty()) throw fail(404,"BOOKING_NOT_FOUND");
        var session=assignedSession(ids.getFirst());
        if(!session.get("state").equals("PUBLISHED") || ((Instant)session.get("ends_at")).isAfter(clock.instant())) throw fail(409,"ATTENDANCE_NOT_OPEN");
        String status=repository.findRecordBookingsLocked(booking);
        if(status.equals("CANCELLED")) throw fail(422,"BOOKING_CANCELLED");
        repository.updateRecordBookingsStatus(input.status(),booking);
        Instant now=clock.instant();
        repository.writeRecordAttendanceRecords(booking,Actor.id(),input.outcome().strip(),now);
        audit("RECORD_ATTENDANCE",booking,"Attendance: "+input.status());
        String name=repository.findRecordBookingsDisplayName(booking);
        return new Outcome(booking,name,input.status(),input.outcome().strip(),now);
    }
    @PreAuthorize("hasRole('TRAINER')")
    public List<Outcome> outcomes(UUID session) {
        if(repository.findOutcomesClassSessionsCount(session,Actor.id())!=1) throw fail(404,"SESSION_NOT_FOUND");
        return repository.findOutcomesBookingsIdDisplayName((r,n)->new Outcome(r.getObject("id",UUID.class),r.getString("display_name"),r.getString("status"),r.getString("outcome"),r.getInstant("recorded_at")),session);
    }
    @PreAuthorize("hasRole('TRAINER')") @Transactional
    public void complete(UUID id) {
        var session=assignedSession(id);
        if(session.get("state").equals("COMPLETED")) return;
        if(!session.get("state").equals("PUBLISHED") || ((Instant)session.get("ends_at")).isAfter(clock.instant())) throw fail(409,"SESSION_NOT_ENDED");
        if(repository.findCompleteBookingsCountConfirmed(id)>0) throw fail(409,"ATTENDANCE_INCOMPLETE");
        repository.updateCompleteClassSessionsStateCompleted(id);
        audit("COMPLETE_SESSION",id,"All attendance recorded");
    }
    @PreAuthorize("hasRole('CUSTOMER')") @Transactional
    public Review review(UUID booking,ReviewInput input) {
        var ids=repository.findReviewBookingsSessionId(booking,Actor.id());
        if(ids.isEmpty()) throw fail(404,"BOOKING_NOT_FOUND");
        // Match attendance lock ordering and ensure eligibility cannot change while creating the review.
        String state=repository.findReviewClassSessionsLocked(ids.getFirst());
        String status=repository.findReviewBookingsLocked(booking);
        if(!state.equals("COMPLETED") || !status.equals("ATTENDED")) throw fail(422,"REVIEW_NOT_ELIGIBLE");
        UUID id=UUID.randomUUID(); Instant now=clock.instant();
        repository.saveReview(id,booking,Actor.id(),input.rating(),input.body().strip(),now);
        return new Review(id,booking,input.rating(),input.body().strip(),now);
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<Review> reviews() {
        return repository.findReviewsReviews((r,n)->new Review(r.getObject("id",UUID.class),r.getObject("booking_id",UUID.class),r.getInt("rating"),r.getString("body"),r.getInstant("created_at")),Actor.id());
    }
    private void audit(String action,UUID resource,String reason) {
        repository.insertAuditAuditEvents(UUID.randomUUID(),Actor.id(),action,resource,reason);
    }
}
