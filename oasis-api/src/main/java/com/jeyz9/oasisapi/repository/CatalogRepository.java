package com.jeyz9.oasisapi.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JPA persistence boundary for the catalog domain. PostgreSQL locking/upserts stay explicit. */
@Repository @Transactional
public class CatalogRepository extends JpaNativeQueries {
    private final WellnessServiceRepository services;
    public CatalogRepository(WellnessServiceRepository services) { this.services=services; }
    public java.util.List<com.jeyz9.oasisapi.dto.CatalogDto.ServiceView> listServices(boolean activeOnly) {
        var page=org.springframework.data.domain.PageRequest.of(0,200);
        return (activeOnly?services.findByActiveTrueOrderByNameAsc(page):services.findAllByOrderByNameAsc(page)).stream().map(this::serviceView).toList();
    }
    private com.jeyz9.oasisapi.dto.CatalogDto.ServiceView serviceView(com.jeyz9.oasisapi.models.WellnessService s) {
        return new com.jeyz9.oasisapi.dto.CatalogDto.ServiceView(s.getId(),s.getName(),s.getDescription(),s.getCategory(),s.getKind(),s.getDurationMinutes(),s.getDifficulty(),s.getCapacity(),s.getActive());
    }
    public com.jeyz9.oasisapi.dto.CatalogDto.ServiceView saveService(UUID id,com.jeyz9.oasisapi.dto.CatalogDto.ServiceInput input) {
        var entity=services.findById(id).orElseGet(com.jeyz9.oasisapi.models.WellnessService::new);
        entity.setId(id);entity.setName(input.name().strip());entity.setDescription(input.description().strip());entity.setCategory(input.category().strip());entity.setKind(input.kind());entity.setDurationMinutes(input.durationMinutes());entity.setDifficulty(input.difficulty());entity.setCapacity(input.capacity());entity.setActive(input.active());
        return serviceView(services.saveAndFlush(entity));
    }

    static final String SESSION_SELECT = """
        SELECT s.*, v.name,v.description,v.category,v.kind,v.difficulty,u.display_name AS trainer_name
        FROM class_sessions s JOIN services v ON v.id=s.service_id JOIN users u ON u.id=s.trainer_id
        """;

    public <T> List<T> findTrainersUsersIdDisplayNameTrainer(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT u.id,u.display_name FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.enabled AND r.role='TRAINER' ORDER BY u.display_name LIMIT 200",mapper,parameters);
    }

    public <T> List<T> findSaveServiceServicesLocked(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT * FROM services WHERE id=? FOR UPDATE",mapper,parameters);
    }

    public Long findSaveServiceClassSessionsCount(Object... parameters) {
        return scalar("SELECT count(*) FROM class_sessions WHERE service_id=?",Long.class,parameters);
    }

    public <T> List<T> findScheduleRowsPublished(ProjectionMapper<T> mapper,Object... parameters) {
        return rows(SESSION_SELECT + " WHERE s.state='PUBLISHED' AND v.active AND s.starts_at>=? AND s.starts_at<? ORDER BY s.starts_at,s.id LIMIT 500",mapper,parameters);
    }

    public <T> List<T> findSessionRowsPublished(ProjectionMapper<T> mapper,Object... parameters) {
        return rows(SESSION_SELECT+" WHERE s.id=? AND s.state='PUBLISHED' AND v.active",mapper,parameters);
    }

    public <T> List<T> findAdminScheduleRows(ProjectionMapper<T> mapper,Object... parameters) {
        return rows(SESSION_SELECT+" WHERE s.starts_at>=? AND s.starts_at<? ORDER BY s.starts_at,s.id LIMIT 500",mapper,parameters);
    }

    public <T> List<T> findTrainerScheduleRows(ProjectionMapper<T> mapper,Object... parameters) {
        return rows(SESSION_SELECT+" WHERE s.trainer_id=? AND s.starts_at>=? AND s.starts_at<? ORDER BY s.starts_at,s.id LIMIT 500",mapper,parameters);
    }

    public List<String> findSaveSessionClassSessionsLocked(Object... parameters) {
        return scalars("SELECT state FROM class_sessions WHERE id=? FOR UPDATE",String.class,parameters);
    }

    public Long findSaveSessionBookingsCount(Object... parameters) {
        return scalar("SELECT count(*) FROM bookings WHERE session_id=?",Long.class,parameters);
    }

    public <T> List<T> findSaveSessionServicesLocked(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT * FROM services WHERE id=? FOR SHARE",mapper,parameters);
    }

    public List<UUID> findSaveSessionUsersLockedTrainer(Object... parameters) {
        return scalars("SELECT u.id FROM users u JOIN user_roles r ON u.id=r.user_id WHERE u.id=? AND u.enabled AND r.role='TRAINER' FOR UPDATE OF u",UUID.class,parameters);
    }

    public int insertSaveSessionRooms(Object... parameters) {
        return execute("INSERT INTO rooms(id,name,location,capacity,active) VALUES (?,?,?,100,true) ON CONFLICT(canonical_name) DO NOTHING",parameters);
    }

    public Map<String,Object> findSaveSessionRoomsLocked(Object... parameters) {
        return map("SELECT * FROM rooms WHERE canonical_name=lower(btrim(?)) FOR SHARE",parameters);
    }

    public List<Long> findSaveSessionTrainerTermsCommissionMinor(Object... parameters) {
        return scalars("SELECT commission_minor FROM trainer_terms WHERE trainer_id=?",Long.class,parameters);
    }

    public Long findSaveSessionTrainerAvailabilityCount(Object... parameters) {
        return scalar("SELECT count(*) FROM trainer_availability WHERE trainer_id=? AND starts_at<=? AND ends_at>=?",Long.class,parameters);
    }

    public Integer findSaveSessionBusinessSettingsCancellationMinutes(Object... parameters) {
        return scalar("SELECT cancellation_minutes FROM business_settings WHERE id=1",Integer.class,parameters);
    }

    public int insertSaveSessionClassSessions(Object... parameters) {
        return execute("INSERT INTO class_sessions(id,service_id,trainer_id,location,starts_at,ends_at,capacity,state,cancellation_minutes,room_id,commission_minor) VALUES (?,?,?,?,?,?,?,?,?,?,?)",parameters);
    }

    public int updateSaveSessionClassSessionsServiceIdTrainerIdLocation(Object... parameters) {
        return execute("UPDATE class_sessions SET service_id=?,trainer_id=?,location=?,starts_at=?,ends_at=?,capacity=?,state=?,room_id=?,commission_minor=CASE WHEN trainer_id<>? THEN ? ELSE commission_minor END WHERE id=?",parameters);
    }

    public <T> T findSaveSessionRows(ProjectionMapper<T> mapper,Object... parameters) {
        return one(SESSION_SELECT+" WHERE s.id=?",mapper,parameters);
    }

    public int insertAuditAuditEvents(Object... parameters) {
        return execute("INSERT INTO audit_events(id,actor_id,action,resource_id,reason) VALUES (?,?,?,?,?)",parameters);
    }
}
