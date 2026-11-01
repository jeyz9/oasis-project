package com.jeyz9.oasisapi.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/** JPA persistence boundary for the membership domain. PostgreSQL locking/upserts stay explicit. */
@Repository @Transactional
public class MembershipRepository extends JpaNativeQueries {
    private final MembershipPlanRepository plans;
    public MembershipRepository(MembershipPlanRepository plans) { this.plans=plans; }
    public void savePlan(UUID id,com.jeyz9.oasisapi.dto.MembershipDto.PlanInput input) {
        var plan=plans.findById(id).orElseGet(com.jeyz9.oasisapi.models.MembershipPlan::new);
        plan.setId(id);plan.setName(input.name().strip());plan.setPeriod(input.period());plan.setEntitlementMode(input.entitlementMode());plan.setCredits(input.credits());plan.setValidityDays(input.validityDays());plan.setPriceMinor(input.priceMinor());plan.setCurrency("THB");plan.setActive(input.active());
        plans.saveAndFlush(plan);
        entityManager.createQuery("delete from PlanService s where s.planId=:id").setParameter("id",id).executeUpdate();
        entityManager.clear();
        for(UUID service:new TreeSet<>(input.serviceIds())) {
            var link=new com.jeyz9.oasisapi.models.PlanService();link.setPlanId(id);link.setServiceId(service);entityManager.persist(link);
        }
        entityManager.flush();
    }

    public List<Map<String,Object>> findPlanMembershipPlansLocked(boolean lock,Object... parameters) {
        return maps("SELECT * FROM membership_plans WHERE id=?"+(lock?" FOR SHARE":""),parameters);
    }

    public List<UUID> findPlanPlanServicesServiceId(Object... parameters) {
        return scalars("SELECT service_id FROM plan_services WHERE plan_id=? ORDER BY service_id",UUID.class,parameters);
    }

    public List<UUID> findCatalogueMembershipPlansId(Object... parameters) {
        return scalars("SELECT id FROM membership_plans WHERE active ORDER BY name,id LIMIT 200",UUID.class,parameters);
    }

    public List<UUID> findAllMembershipPlansId(Object... parameters) {
        return scalars("SELECT id FROM membership_plans ORDER BY name,id LIMIT 200",UUID.class,parameters);
    }

    public Long findSaveServicesCount(Object... parameters) {
        return scalar("SELECT count(*) FROM services WHERE id=?",Long.class,parameters);
    }

    public List<UUID> findSaveMembershipPlansLocked(Object... parameters) {
        return scalars("SELECT id FROM membership_plans WHERE id=? FOR UPDATE",UUID.class,parameters);
    }

    public <T> List<T> findCustomersUsersIdDisplayNameCustomer(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT u.id,u.display_name,u.email FROM users u JOIN user_roles r ON u.id=r.user_id WHERE u.enabled AND r.role='CUSTOMER' ORDER BY u.display_name,u.id LIMIT 200",mapper,parameters);
    }

    public <T> List<T> findGrantsMembershipGrants(ProjectionMapper<T> mapper,Object... parameters) {
        return rows("SELECT g.*,u.display_name FROM membership_grants g JOIN users u ON u.id=g.customer_id ORDER BY g.valid_from DESC,g.id LIMIT 200",mapper,parameters);
    }

    public List<UUID> findIssueUsersLockedCustomer(Object... parameters) {
        return scalars("SELECT u.id FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.id=? AND u.enabled AND r.role='CUSTOMER' FOR SHARE OF u",UUID.class,parameters);
    }

    public Long findIssueMembershipPlansCount(Object... parameters) {
        return scalar("SELECT count(*) FROM membership_plans WHERE id=?",Long.class,parameters);
    }

    public int insertIssueComplimentaryRequests(Object... parameters) {
        return execute("INSERT INTO complimentary_requests(actor_id,request_key,customer_id,plan_id,starts_at,reason) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",parameters);
    }

    public Map<String,Object> findIssueComplimentaryRequestsLocked(Object... parameters) {
        return map("SELECT * FROM complimentary_requests WHERE actor_id=? AND request_key=? FOR UPDATE",parameters);
    }

    public int writeIssueMembershipGrants(Object... parameters) {
        return execute("""
            INSERT INTO membership_grants(id,customer_id,name,source,valid_from,valid_until,status,remaining_credits,entitlement_mode,plan_id,plan_snapshot)
            SELECT ?,?,name,'ADMIN_COMPLIMENTARY',?,?,'ACTIVE',credits,entitlement_mode,id,
            jsonb_build_object('name',name,'period',period,'entitlementMode',entitlement_mode,'credits',credits,'validityDays',validity_days,'priceMinor',price_minor,'currency',currency)
            FROM membership_plans WHERE id=?
            """,parameters);
    }

    public int insertIssueGrantServices(Object... parameters) {
        return execute("INSERT INTO grant_services VALUES (?,?)",parameters);
    }

    public int insertIssueCreditLedgerGrant(Object... parameters) {
        return execute("INSERT INTO credit_ledger(id,grant_id,kind,delta,reason,created_at) VALUES (?,?,'GRANT',?,?,?)",parameters);
    }

    public int updateIssueComplimentaryRequestsGrantId(Object... parameters) {
        return execute("UPDATE complimentary_requests SET grant_id=? WHERE actor_id=? AND request_key=?",parameters);
    }

    public <T> T findIssuedMembershipGrantsIdValidUntil(ProjectionMapper<T> mapper,Object... parameters) {
        return one("SELECT id,valid_until FROM membership_grants WHERE id=?",mapper,parameters);
    }

    public List<String> findStateMembershipGrantsLocked(Object... parameters) {
        return scalars("SELECT status FROM membership_grants WHERE id=? FOR NO KEY UPDATE",String.class,parameters);
    }

    public Long findStateRefundsCountPending(Object... parameters) {
        return scalar("SELECT count(*) FROM refunds f JOIN payments p ON p.id=f.payment_id JOIN membership_orders o ON o.id=p.order_id WHERE o.grant_id=? AND f.state='PENDING'",Long.class,parameters);
    }

    public int updateStateMembershipGrantsStatus(Object... parameters) {
        return execute("UPDATE membership_grants SET status=? WHERE id=?",parameters);
    }

    public int insertAuditAuditEvents(Object... parameters) {
        return execute("INSERT INTO audit_events(id,actor_id,action,resource_id,reason) VALUES (?,?,?,?,?)",parameters);
    }
}
