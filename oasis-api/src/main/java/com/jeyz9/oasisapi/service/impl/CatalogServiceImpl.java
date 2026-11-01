package com.jeyz9.oasisapi.service.impl;

import com.jeyz9.oasisapi.service.CatalogService;
import com.jeyz9.oasisapi.repository.CatalogRepository;
import com.jeyz9.oasisapi.repository.ProjectionMapper;
import com.jeyz9.oasisapi.dto.CatalogDto.ServiceInput;
import com.jeyz9.oasisapi.dto.CatalogDto.ServiceView;
import com.jeyz9.oasisapi.dto.CatalogDto.SessionInput;
import com.jeyz9.oasisapi.dto.CatalogDto.SessionView;
import com.jeyz9.oasisapi.dto.CatalogDto.Trainer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import com.jeyz9.oasisapi.utils.Actor;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service
@Validated
public class CatalogServiceImpl implements CatalogService {
    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private final CatalogRepository repository;
    private final Clock clock;
    private final int cancellationMinutes;
    public CatalogServiceImpl(CatalogRepository repository, Clock clock, @Value("${oasis.cancellation-minutes:120}") int cancellationMinutes) {
        if (cancellationMinutes < 0 || cancellationMinutes > 10080) throw new IllegalArgumentException("Invalid cancellation policy");
        this.repository = repository; this.clock = clock; this.cancellationMinutes = cancellationMinutes;
    }

    static final ProjectionMapper<ServiceView> SERVICE = (r,n) -> new ServiceView(r.getObject("id",UUID.class), r.getString("name"),
        r.getString("description"),r.getString("category"),r.getString("kind"),r.getInt("duration_minutes"),
        r.getString("difficulty"),r.getInt("capacity"),r.getBoolean("active"));

    public static final ProjectionMapper<SessionView> SESSION = (r,n) -> new SessionView(r.getObject("id",UUID.class),
        r.getObject("service_id",UUID.class),r.getString("name"),r.getString("description"),r.getString("category"),
        r.getString("kind"),r.getString("difficulty"),r.getObject("trainer_id",UUID.class),r.getString("trainer_name"),
        r.getString("location"),r.getInstant("starts_at"),r.getInstant("ends_at"),
        r.getInt("capacity"),r.getInt("reserved_count"),r.getInt("capacity")-r.getInt("reserved_count"),
        r.getString("state"),r.getInt("cancellation_minutes"));
    public List<ServiceView> services() { return repository.listServices(true); }
    @PreAuthorize("hasRole('ADMIN')")
    public List<ServiceView> adminServices() { return repository.listServices(false); }
    @PreAuthorize("hasRole('ADMIN')")
    public List<Trainer> trainers() {
        return repository.findTrainersUsersIdDisplayNameTrainer((r,n) -> new Trainer(r.getObject("id",UUID.class),r.getString("display_name")));
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public ServiceView saveService(UUID id, ServiceInput input) {
        if (input.kind().equals("PRIVATE") && input.capacity()!=1) throw fail(400,"PRIVATE_CAPACITY_ONE");
        if (id == null) {
            id = UUID.randomUUID();
        } else {
            var previous = repository.findSaveServiceServicesLocked(SERVICE,id);
            if (previous.isEmpty()) throw fail(404,"SERVICE_NOT_FOUND");
            // Type changes would invalidate existing capacity snapshots.
            if (!previous.getFirst().kind().equals(input.kind()) && repository.findSaveServiceClassSessionsCount(id)>0)
                throw fail(409,"SERVICE_TYPE_IN_USE");
        }
        var saved=repository.saveService(id,input);
        audit("SAVE_SERVICE",id,"Service configuration");
        return saved;
    }
    private Instant[] range(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from) || to.isAfter(from.plusDays(31))) throw fail(400,"INVALID_DATE_RANGE");
        return new Instant[]{from.atStartOfDay(BANGKOK).toInstant(),to.atStartOfDay(BANGKOK).toInstant()};
    }
    public List<SessionView> schedule(LocalDate from, LocalDate to) {
        var r = range(from,to);
        return repository.findScheduleRowsPublished(SESSION,r[0],r[1]);
    }
    public SessionView session(UUID id) {
        var rows=repository.findSessionRowsPublished(SESSION,id);
        if(rows.isEmpty()) throw fail(404,"SESSION_NOT_FOUND");
        return rows.getFirst();
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<SessionView> adminSchedule(LocalDate from, LocalDate to) {
        var r=range(from,to);
        return repository.findAdminScheduleRows(SESSION,r[0],r[1]);
    }
    @PreAuthorize("hasRole('TRAINER')")
    public List<SessionView> trainerSchedule(LocalDate from, LocalDate to) {
        var r=range(from,to);
        return repository.findTrainerScheduleRows(SESSION,Actor.id(),r[0],r[1]);
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public SessionView saveSession(UUID id, SessionInput input) {
        if(!input.startsAt().isAfter(clock.instant())) throw fail(400,"SESSION_MUST_BE_FUTURE");
        if (id != null) {
            var existing = repository.findSaveSessionClassSessionsLocked(id);
            if(existing.isEmpty()) throw fail(404,"SESSION_NOT_FOUND");
            if(existing.getFirst().equals("CANCELLED") || existing.getFirst().equals("COMPLETED") || repository.findSaveSessionBookingsCount(id)>0)
                throw fail(409,"SESSION_HAS_HISTORY");
        }
        var services = repository.findSaveSessionServicesLocked(SERVICE,input.serviceId());
        if(services.isEmpty() || !services.getFirst().active()) throw fail(422,"SERVICE_INACTIVE");
        var service=services.getFirst();
        if(repository.findSaveSessionUsersLockedTrainer(input.trainerId()).isEmpty())
            throw fail(422,"TRAINER_UNAVAILABLE");
        if(input.capacity()>service.capacity() || (service.kind().equals("PRIVATE") && input.capacity()!=1)) throw fail(400,"INVALID_CAPACITY");
        String roomName=input.location().strip();
        repository.insertSaveSessionRooms(UUID.randomUUID(),roomName,roomName);
        var room=repository.findSaveSessionRoomsLocked(roomName);
        if(!(Boolean)room.get("active") || input.capacity()>(Integer)room.get("capacity")) throw fail(422,"ROOM_UNAVAILABLE");
        var terms=repository.findSaveSessionTrainerTermsCommissionMinor(input.trainerId());
        long commission=terms.isEmpty()?0:terms.getFirst();
        Instant end=input.startsAt().plusSeconds(service.durationMinutes()*60L);
        if(service.kind().equals("PRIVATE") && repository.findSaveSessionTrainerAvailabilityCount(input.trainerId(),input.startsAt(),end)==0) throw fail(422,"TRAINER_OUTSIDE_AVAILABILITY");
        if(id==null) {
            id=UUID.randomUUID();
            Integer storedPolicy=repository.findSaveSessionBusinessSettingsCancellationMinutes();
            repository.insertSaveSessionClassSessions(id,input.serviceId(),input.trainerId(),room.get("name"),input.startsAt(),end,input.capacity(),input.state(),storedPolicy==null?cancellationMinutes:storedPolicy,room.get("id"),commission);
        } else {
            repository.updateSaveSessionClassSessionsServiceIdTrainerIdLocation(input.serviceId(),input.trainerId(),room.get("name"),input.startsAt(),end,input.capacity(),input.state(),room.get("id"),input.trainerId(),commission,id);
        }
        audit("SAVE_SESSION",id,"Session configuration");
        return repository.findSaveSessionRows(SESSION,id);
    }
    private void audit(String action, UUID id, String reason) {
        repository.insertAuditAuditEvents(UUID.randomUUID(),Actor.id(),action,id,reason);
    }
}
