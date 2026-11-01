package com.jeyz9.oasisapi.service.impl;

import com.jeyz9.oasisapi.service.AdministrationService;
import com.jeyz9.oasisapi.repository.AdministrationRepository;
import com.jeyz9.oasisapi.dto.AdministrationDto.Commission;
import com.jeyz9.oasisapi.dto.AdministrationDto.Policy;
import com.jeyz9.oasisapi.dto.AdministrationDto.Room;
import com.jeyz9.oasisapi.dto.AdministrationDto.RoomInput;
import com.jeyz9.oasisapi.dto.AdministrationDto.User;
import com.jeyz9.oasisapi.dto.AdministrationDto.UserInput;
import com.jeyz9.oasisapi.utils.Actor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service @Validated
public class AdministrationServiceImpl implements AdministrationService {
    private final AdministrationRepository repository;private final Clock clock;private final int fallbackCancellation;
    public AdministrationServiceImpl(AdministrationRepository repository,Clock clock,@Value("${oasis.cancellation-minutes:120}") int fallback) {this.repository=repository;this.clock=clock;fallbackCancellation=fallback;}

    @PreAuthorize("hasRole('ADMIN')") public List<User> users(int offset,int limit) {
        return repository.findUsersUsersIdEmail((r,n)->new User(r.getObject("id",UUID.class),r.getString("email"),r.getString("display_name"),r.getBoolean("enabled"),
            repository.findUsersUserRolesRole(r.getObject("id",UUID.class))),limit,offset);
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional public void user(UUID id,UserInput input) {
        repository.findUserBusinessSettingsLocked();
        if(repository.actingAdminCount(Actor.id())!=1) throw fail(403,"FORBIDDEN");
        var rows=repository.findUserUsersLocked(id);
        if(rows.isEmpty()) throw fail(404,"USER_NOT_FOUND");
        var roles=new HashSet<>(input.roles());if(roles.size()!=input.roles().size()) throw fail(400,"DUPLICATE_ROLE");
        var old=repository.findUserUserRolesRole(id);
        if(rows.getFirst() && old.contains("ADMIN") && (!input.enabled() || !roles.contains("ADMIN")) &&
            repository.otherEnabledAdminCount(id)==0) throw fail(409,"LAST_ADMIN");
        if((!input.enabled() || !roles.contains("TRAINER")) && old.contains("TRAINER") && repository.findUserClassSessionsCountDraftPublished(id,clock.instant())>0) throw fail(409,"TRAINER_HAS_SESSIONS");
        if((!input.enabled() || !roles.contains("CUSTOMER")) && old.contains("CUSTOMER") && repository.findUserBookingsCountConfirmed(id,clock.instant())>0) throw fail(409,"CUSTOMER_HAS_BOOKINGS");
        if(!roles.contains("CUSTOMER") && old.contains("CUSTOMER") && repository.findUserMembershipGrantsCountActiveSuspended(id,clock.instant())>0) throw fail(409,"CUSTOMER_HAS_MEMBERSHIPS");
        // Diff roles rather than deleting retained rows that concurrent trainer FK checks may inspect.
        for(var role:old) if(!roles.contains(role)) repository.deleteUserUserRoles(id,role);
        for(var role:roles) if(!old.contains(role)) repository.insertUserUserRoles(id,role);
        repository.updateUserUsersEnabled(input.enabled(),id);audit("CHANGE_USER_ACCESS",id,input.reason().strip());
    }
    @PreAuthorize("hasRole('ADMIN')") public List<Room> rooms() {
        return repository.rooms();
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional public Room room(UUID id,RoomInput input) {
        if(id==null) {id=UUID.randomUUID();repository.saveRoom(id,input);}
        else {
            var rows=repository.findRoomRoomsLocked(id);if(rows.isEmpty()) throw fail(404,"ROOM_NOT_FOUND");
            if(!rows.getFirst().get("name").equals(input.name().strip()) && repository.findRoomClassSessionsCount(id)>0) throw fail(409,"ROOM_NAME_HAS_HISTORY");
            if(repository.findRoomClassSessionsCountDraftPublished(id,clock.instant(),input.capacity(),input.active())>0) throw fail(409,"ROOM_HAS_SESSIONS");
            repository.saveRoom(id,input);
        }
        audit("SAVE_ROOM",id,"Room configuration");return new Room(id,input.name().strip(),input.location().strip(),input.capacity(),input.active());
    }
    @PreAuthorize("hasRole('ADMIN')") public Map<String,Integer> policy() {
        Integer value=repository.findPolicyBusinessSettingsCancellationMinutes();return Map.of("cancellationMinutes",value==null?fallbackCancellation:value);
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional public void policy(Policy input) {
        repository.updatePolicyBusinessSettingsCancellationMinutes(input.cancellationMinutes());audit("CHANGE_CANCELLATION_POLICY",Actor.id(),input.reason().strip());
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional public void commission(UUID trainer,Commission input) {
        if(repository.findCommissionUsersLockedTrainer(trainer).isEmpty()) throw fail(422,"TRAINER_UNAVAILABLE");
        repository.insertCommissionTrainerTerms(trainer,input.commissionMinor());audit("CHANGE_TRAINER_COMMISSION",trainer,input.reason().strip());
    }
    @PreAuthorize("hasRole('ADMIN')") public List<Map<String,Object>> audit(int offset,int limit) {
        return repository.findAuditAuditEventsIdAction(limit,offset);
    }
    private void audit(String action,UUID resource,String reason) {repository.insertAuditAuditEvents(UUID.randomUUID(),Actor.id(),action,resource,reason);}
}
