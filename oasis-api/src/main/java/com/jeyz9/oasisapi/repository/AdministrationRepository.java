package com.jeyz9.oasisapi.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JPA persistence boundary for the administration domain. PostgreSQL locking/upserts stay explicit. */
@Repository @Transactional
public class AdministrationRepository extends JpaNativeQueries {
    private final RoomRepository rooms;
    public AdministrationRepository(RoomRepository rooms) { this.rooms=rooms; }
    public java.util.List<com.jeyz9.oasisapi.dto.AdministrationDto.Room> rooms() {
        return rooms.findAllByOrderByNameAscIdAsc(org.springframework.data.domain.PageRequest.of(0,200)).stream().map(r->new com.jeyz9.oasisapi.dto.AdministrationDto.Room(r.getId(),r.getName(),r.getLocation(),r.getCapacity(),r.getActive())).toList();
    }
    public void saveRoom(UUID id,com.jeyz9.oasisapi.dto.AdministrationDto.RoomInput input) {
        var room=rooms.findById(id).orElseGet(com.jeyz9.oasisapi.models.Room::new);
        room.setId(id);room.setName(input.name().strip());room.setLocation(input.location().strip());room.setCapacity(input.capacity());room.setActive(input.active());
        rooms.saveAndFlush(room);
    }

    public <T> List<T> findUsersUsersIdEmail(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT id,email,display_name,enabled FROM users ORDER BY email,id LIMIT ? OFFSET ?",mapper,parameters);
    }

    public List<String> findUsersUserRolesRole(Object... parameters) {
        return scalars("SELECT role FROM user_roles WHERE user_id=? ORDER BY role",String.class,parameters);
    }

    public Integer findUserBusinessSettingsLocked(Object... parameters) {
        return scalar("SELECT id FROM business_settings WHERE id=1 FOR NO KEY UPDATE",Integer.class,parameters);
    }

    public Long actingAdminCount(Object... parameters) {
        return scalar("SELECT count(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.id=? AND u.enabled AND r.role='ADMIN'",Long.class,parameters);
    }

    public List<Boolean> findUserUsersLocked(Object... parameters) {
        return scalars("SELECT enabled FROM users WHERE id=? FOR NO KEY UPDATE",Boolean.class,parameters);
    }

    public List<String> findUserUserRolesRole(Object... parameters) {
        return scalars("SELECT role FROM user_roles WHERE user_id=?",String.class,parameters);
    }

    public Long otherEnabledAdminCount(Object... parameters) {
        return scalar("SELECT count(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.enabled AND r.role='ADMIN' AND u.id<>?",Long.class,parameters);
    }

    public Long findUserClassSessionsCountDraftPublished(Object... parameters) {
        return scalar("SELECT count(*) FROM class_sessions WHERE trainer_id=? AND state IN ('DRAFT','PUBLISHED') AND ends_at>?",Long.class,parameters);
    }

    public Long findUserBookingsCountConfirmed(Object... parameters) {
        return scalar("SELECT count(*) FROM bookings WHERE customer_id=? AND status='CONFIRMED' AND ends_at>?",Long.class,parameters);
    }

    public Long findUserMembershipGrantsCountActiveSuspended(Object... parameters) {
        return scalar("SELECT count(*) FROM membership_grants WHERE customer_id=? AND status IN ('ACTIVE','SUSPENDED') AND valid_until>?",Long.class,parameters);
    }

    public int deleteUserUserRoles(Object... parameters) {
        return execute("DELETE FROM user_roles WHERE user_id=? AND role=?",parameters);
    }

    public int insertUserUserRoles(Object... parameters) {
        return execute("INSERT INTO user_roles VALUES (?,?)",parameters);
    }

    public int updateUserUsersEnabled(Object... parameters) {
        return execute("UPDATE users SET enabled=? WHERE id=?",parameters);
    }

    public List<Map<String,Object>> findRoomRoomsLocked(Object... parameters) {
        return maps("SELECT * FROM rooms WHERE id=? FOR NO KEY UPDATE",parameters);
    }

    public Long findRoomClassSessionsCount(Object... parameters) {
        return scalar("SELECT count(*) FROM class_sessions WHERE room_id=?",Long.class,parameters);
    }

    public Long findRoomClassSessionsCountDraftPublished(Object... parameters) {
        return scalar("SELECT count(*) FROM class_sessions WHERE room_id=? AND state IN ('DRAFT','PUBLISHED') AND ends_at>? AND (capacity>? OR ?=false)",Long.class,parameters);
    }

    public Integer findPolicyBusinessSettingsCancellationMinutes(Object... parameters) {
        return scalar("SELECT cancellation_minutes FROM business_settings WHERE id=1",Integer.class,parameters);
    }

    public int updatePolicyBusinessSettingsCancellationMinutes(Object... parameters) {
        return execute("UPDATE business_settings SET cancellation_minutes=? WHERE id=1",parameters);
    }

    public List<UUID> findCommissionUsersLockedTrainer(Object... parameters) {
        return scalars("SELECT u.id FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.id=? AND u.enabled AND r.role='TRAINER' FOR NO KEY UPDATE OF u",UUID.class,parameters);
    }

    public int insertCommissionTrainerTerms(Object... parameters) {
        return execute("INSERT INTO trainer_terms VALUES (?,?) ON CONFLICT(trainer_id) DO UPDATE SET commission_minor=excluded.commission_minor",parameters);
    }

    public List<Map<String,Object>> findAuditAuditEventsIdAction(Object... parameters) {
        return maps("SELECT a.id,a.action,a.resource_id,a.reason,a.created_at,u.display_name AS actor FROM audit_events a JOIN users u ON a.actor_id=u.id ORDER BY a.created_at DESC,a.id LIMIT ? OFFSET ?",parameters);
    }

    public int insertAuditAuditEvents(Object... parameters) {
        return execute("INSERT INTO audit_events(id,actor_id,action,resource_id,reason) VALUES (?,?,?,?,?)",parameters);
    }
}
