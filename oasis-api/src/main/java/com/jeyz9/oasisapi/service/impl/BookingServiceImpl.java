package com.jeyz9.oasisapi.service.impl;

import com.jeyz9.oasisapi.service.BookingService;
import com.jeyz9.oasisapi.repository.BookingEntityRepository;
import com.jeyz9.oasisapi.repository.BookingRepository;
import com.jeyz9.oasisapi.repository.ClassSessionRepository;
import com.jeyz9.oasisapi.repository.MembershipGrantRepository;
import com.jeyz9.oasisapi.repository.ProjectionMapper;
import com.jeyz9.oasisapi.dto.BookingDto.Attendee;
import com.jeyz9.oasisapi.dto.BookingDto.BookInput;
import com.jeyz9.oasisapi.dto.BookingDto.Booking;
import com.jeyz9.oasisapi.dto.BookingDto.Entry;
import com.jeyz9.oasisapi.dto.BookingDto.Grant;
import com.jeyz9.oasisapi.dto.BookingDto.Receipt;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.jeyz9.oasisapi.utils.Actor;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service
@Validated
public class BookingServiceImpl implements BookingService {
    private final BookingRepository repository;
    private final Clock clock;
    private final ClassSessionRepository sessions;
    private final BookingEntityRepository bookings;
    private final MembershipGrantRepository grants;
    private final com.jeyz9.oasisapi.service.NotificationService notifications;
    public BookingServiceImpl(BookingRepository repository, Clock clock,com.jeyz9.oasisapi.service.NotificationService notifications, ClassSessionRepository sessions, BookingEntityRepository bookings, MembershipGrantRepository grants) { this.repository=repository; this.clock=clock;this.notifications=notifications;this.sessions=sessions;this.bookings=bookings;this.grants=grants; }

    private record LockedSession(UUID id, UUID serviceId, Instant start, Instant end, int capacity, int reserved, String state, int window) {}
    private record LockedBooking(UUID id, UUID customer, UUID session, UUID grant, String status, int cost) {}

    private static final ProjectionMapper<Booking> BOOKING = (r,n) -> new Booking(r.getObject("id",UUID.class),r.getObject("session_id",UUID.class),
        r.getObject("grant_id",UUID.class),r.getString("name"),r.getString("trainer_name"),r.getString("location"),
        r.getInstant("starts_at"),r.getInstant("ends_at"),r.getString("status"),
        r.getInstant("booked_at"),r.getInstant("cancelled_at")==null?null:r.getInstant("cancelled_at"),r.getInt("cancellation_minutes"),r.getString("session_state"));
    private LockedSession lockSession(UUID id) {
        // FK inserts take KEY SHARE. NO KEY UPDATE protects seat/balance mutations without
        // upgrading two compatible FK locks into a deadlock; primary keys never change.
        var session=sessions.lockById(id).orElseThrow(()->fail(404,"SESSION_NOT_FOUND"));
        return new LockedSession(session.getId(),session.getServiceId(),session.getStartsAt(),session.getEndsAt(),session.getCapacity(),session.getReservedCount(),session.getState(),session.getCancellationMinutes());
    }
    @PreAuthorize("hasRole('CUSTOMER')") @Transactional
    public Receipt book(String key, BookInput input) {
        UUID customer=Actor.id();
        // Serialize eligibility with an administrator changing this account's access.
        if(repository.lockEligibleCustomer(customer).isEmpty())
            throw fail(403,"FORBIDDEN");
        // Ownership before inserting request FKs, and before replaying a saved response.
        if(repository.countOwnedGrants(input.grantId(),customer)!=1)
            throw fail(404,"GRANT_NOT_FOUND");
        if(repository.countSessions(input.sessionId())!=1) throw fail(404,"SESSION_NOT_FOUND");
        repository.insertBookingRequest(customer,key,input.sessionId(),input.grantId());
        var request=repository.lockBookingRequest(customer,key);
        if(!input.sessionId().equals(request.get("session_id")) || !input.grantId().equals(request.get("grant_id"))) throw fail(409,"IDEMPOTENCY_CONFLICT");
        if(request.get("booking_id")!=null) return new Receipt((UUID)request.get("booking_id"),input.sessionId(),input.grantId(),"CONFIRMED");
        var session=lockSession(input.sessionId());
        Instant now=clock.instant();
        if(!session.state().equals("PUBLISHED") || !session.start().isAfter(now)) throw fail(409,"SESSION_NOT_BOOKABLE");
        var active=repository.lockActiveService(session.serviceId());
        if(active.isEmpty() || !active.getFirst()) throw fail(409,"SERVICE_INACTIVE");
        if(session.reserved()>=session.capacity()) throw fail(409,"SESSION_FULL");
        if(repository.countConfirmedBookings(customer,session.id())>0)
            throw fail(409,"ALREADY_BOOKED");
        var grant=grants.lockOwned(input.grantId(),customer).orElseThrow(()->fail(404,"GRANT_NOT_FOUND"));
        Instant from=grant.getValidFrom(), until=grant.getValidUntil();
        if(!grant.getStatus().equals("ACTIVE") || from.isAfter(now) || !until.isAfter(now) || !until.isAfter(session.start())) throw fail(422,"ENTITLEMENT_EXPIRED_OR_INACTIVE");
        if(repository.countGrantEligibility(input.grantId(),session.serviceId())!=1)
            throw fail(422,"SERVICE_NOT_ELIGIBLE");
        boolean credits=grant.getEntitlementMode().equals("CREDITS");
        if(credits && repository.debitAvailableCredit(input.grantId())!=1)
            throw fail(422,"INSUFFICIENT_CREDITS");
        if(repository.reserveAvailableSeat(session.id())!=1)
            throw fail(409,"SESSION_FULL");
        UUID booking=UUID.randomUUID();
        repository.insertConfirmedBooking(booking,customer,session.id(),input.grantId(),now,session.start(),session.end(),credits?1:0);
        if(credits) repository.appendBookingDebit(UUID.randomUUID(),input.grantId(),booking,now);
        repository.completeBookingRequest(booking,customer,key);
        notifications.confirmed(booking);
        return new Receipt(booking,session.id(),input.grantId(),"CONFIRMED");
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<Booking> history(int offset, int limit) {
        return repository.bookingHistory(BOOKING,Actor.id(),limit,offset);
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    public Booking own(UUID id) {
        var rows=repository.findOwnedBooking(BOOKING,id,Actor.id());
        if(rows.isEmpty()) throw fail(404,"BOOKING_NOT_FOUND");
        return rows.getFirst();
    }
    @PreAuthorize("hasRole('CUSTOMER')") @Transactional
    public Booking cancel(UUID id) {
        UUID customer=Actor.id();
        var ids=repository.findCancelBookingsSessionId(id,customer);
        if(ids.isEmpty()) throw fail(404,"BOOKING_NOT_FOUND");
        var session=lockSession(ids.getFirst());
        var b=lockBooking(id);
        if(!b.customer().equals(customer)) throw fail(404,"BOOKING_NOT_FOUND");
        if(b.status().equals("CANCELLED")) return own(id);
        if(!b.status().equals("CONFIRMED")) throw fail(409,"BOOKING_ALREADY_RECORDED");
        if(clock.instant().isAfter(session.start().minusSeconds(session.window()*60L))) throw fail(422,"CANCELLATION_WINDOW_CLOSED");
        restore(b,"Eligible customer cancellation");
        return own(id);
    }
    private LockedBooking lockBooking(UUID id) {
        var booking=bookings.lockById(id).orElseThrow(()->fail(404,"BOOKING_NOT_FOUND"));
        return new LockedBooking(booking.getId(),booking.getCustomerId(),booking.getSessionId(),booking.getGrantId(),booking.getStatus(),booking.getCost());
    }
    private void restore(LockedBooking b, String reason) {
        if(b.status().equals("CANCELLED")) return;
        repository.findRestoreMembershipGrantsLocked(b.grant());
        UUID debit=b.cost()==0?null:repository.findRestoreCreditLedgerIdDebit(b.id());
        repository.updateRestoreBookingsStatusCancelledAtCancelled(clock.instant(),b.id());
        if(repository.updateRestoreClassSessionsReservedCount(b.session())!=1)
            throw fail(409,"SEAT_ACCOUNTING_CONFLICT");
        if(b.cost()==1) repository.insertRestoreCreditLedgerRestore(UUID.randomUUID(),b.grant(),b.id(),debit,reason,clock.instant());
        if(b.cost()==1) repository.updateRestoreMembershipGrantsRemainingCredits(b.grant());
        notifications.cancelled(b.id());
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void cancelSession(UUID id, String reason) {
        var s=lockSession(id);
        if(s.state().equals("CANCELLED")) return;
        if(s.state().equals("COMPLETED") || !s.start().isAfter(clock.instant())) throw fail(409,"SESSION_ALREADY_STARTED");
        repository.updateCancelSessionClassSessionsStateCancelled(id);
        // Bounded capacity <=100: the entire refund is atomic, with grants locked in UUID order.
        var bookings=repository.findCancelSessionBookingsLockedConfirmed(id);
        var grants=repository.findCancelSessionBookingsConfirmed(id);
        for(var grant:grants) repository.findCancelSessionMembershipGrantsLocked(grant);
        for(var booking:bookings) restore(lockBooking(booking),"Admin session cancellation");
        repository.insertCancelSessionAuditEventsCancelSession(UUID.randomUUID(),Actor.id(),id,reason.strip());
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<Grant> grants(int offset, int limit) {
        return repository.findGrantsBookingsCountCancelled((r,n)->new Grant(
            r.getObject("id",UUID.class),r.getString("name"),r.getString("source"),r.getInstant("valid_from"),
            r.getInstant("valid_until"),effectiveStatus(r.getString("status"),r.getInstant("valid_from"),r.getInstant("valid_until")),
            (Integer)r.getObject("remaining_credits"),r.getString("entitlement_mode"),r.getLong("used_sessions")),Actor.id(),limit,offset);
    }
    private String effectiveStatus(String status,Instant from,Instant until) {
        if(!status.equals("ACTIVE")) return status;
        if(clock.instant().isBefore(from)) return "NOT_STARTED";
        return !clock.instant().isBefore(until)?"EXPIRED":"ACTIVE";
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<Entry> ledger(int offset, int limit) {
        return repository.findLedgerCreditLedger((r,n)->new Entry(
            r.getObject("id",UUID.class),r.getObject("grant_id",UUID.class),r.getObject("booking_id",UUID.class),r.getString("kind"),r.getInt("delta"),
            r.getString("reason"),r.getInstant("created_at")),Actor.id(),limit,offset);
    }
    @PreAuthorize("hasRole('TRAINER')")
    public List<Attendee> attendees(UUID sessionId) {
        if(repository.findAttendeesClassSessionsCount(sessionId,Actor.id())!=1)
            throw fail(404,"SESSION_NOT_FOUND");
        return repository.findAttendeesBookingsIdDisplayNameCancelled((r,n)->new Attendee(r.getObject("id",UUID.class),r.getString("display_name"),r.getString("status")),sessionId);
    }
}
