package com.jeyz9.oasisapi.service.impl;

import com.jeyz9.oasisapi.service.MembershipService;
import com.jeyz9.oasisapi.repository.MembershipRepository;
import com.jeyz9.oasisapi.dto.MembershipDto.AdminGrant;
import com.jeyz9.oasisapi.dto.MembershipDto.Complimentary;
import com.jeyz9.oasisapi.dto.MembershipDto.Customer;
import com.jeyz9.oasisapi.dto.MembershipDto.GrantState;
import com.jeyz9.oasisapi.dto.MembershipDto.Issued;
import com.jeyz9.oasisapi.dto.MembershipDto.Plan;
import com.jeyz9.oasisapi.dto.MembershipDto.PlanInput;
import com.jeyz9.oasisapi.utils.Actor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service @Validated
public class MembershipServiceImpl implements MembershipService {
    private final MembershipRepository repository;
    private final Clock clock;
    public MembershipServiceImpl(MembershipRepository repository,Clock clock) { this.repository=repository; this.clock=clock; }

    private Plan plan(UUID id,boolean lock) {
        var rows=repository.findPlanMembershipPlansLocked(lock,id);
        if(rows.isEmpty()) throw fail(404,"PLAN_NOT_FOUND");
        var p=rows.getFirst();
        var services=repository.findPlanPlanServicesServiceId(id);
        return new Plan(id,(String)p.get("name"),(String)p.get("period"),(String)p.get("entitlement_mode"),(Integer)p.get("credits"),
            (Integer)p.get("validity_days"),((Number)p.get("price_minor")).longValue(),(String)p.get("currency"),(Boolean)p.get("active"),services);
    }
    public List<Plan> catalogue() {
        return repository.findCatalogueMembershipPlansId().stream().map(id->plan(id,false)).toList();
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<Plan> all() {
        return repository.findAllMembershipPlansId().stream().map(id->plan(id,false)).toList();
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Plan save(UUID id,PlanInput input) {
        if(input.entitlementMode().equals("CREDITS") ? input.credits()==null || input.credits()<1 || input.credits()>10000 : input.credits()!=null)
            throw fail(400,"INVALID_ENTITLEMENT");
        if(input.period().equals("PACKAGE") ? input.validityDays()==null || input.validityDays()<1 || input.validityDays()>3660 : input.validityDays()!=null)
            throw fail(400,"INVALID_VALIDITY");
        var services=new TreeSet<>(input.serviceIds());
        if(services.size()!=input.serviceIds().size()) throw fail(400,"DUPLICATE_SERVICE");
        for(var service:services) {
            if(repository.findSaveServicesCount(service)!=1) throw fail(422,"SERVICE_NOT_FOUND");
        }
        if(id==null) {
            id=UUID.randomUUID();
        } else {
            var existing=repository.findSaveMembershipPlansLocked(id);
            if(existing.isEmpty()) throw fail(404,"PLAN_NOT_FOUND");
        }
        repository.savePlan(id,input);
        audit("SAVE_PLAN",id,"Plan configuration");
        return plan(id,false);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<Customer> customers() {
        return repository.findCustomersUsersIdDisplayNameCustomer((r,n)->new Customer(r.getObject("id",UUID.class),r.getString("display_name"),r.getString("email")));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminGrant> grants() {
        return repository.findGrantsMembershipGrants((r,n)->new AdminGrant(r.getObject("id",UUID.class),r.getString("display_name"),r.getString("name"),r.getString("entitlement_mode"),
                (Integer)r.getObject("remaining_credits"),r.getInstant("valid_from"),r.getInstant("valid_until"),r.getString("status"),r.getString("source")));
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Issued issue(String key,Complimentary input) {
        UUID actor=Actor.id(); String reason=input.reason().strip();
        if(!input.startsAt().equals(input.startsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS))) throw fail(400,"INVALID_ACTIVATION_PRECISION");
        if(repository.findIssueUsersLockedCustomer(input.customerId()).isEmpty())
            throw fail(422,"CUSTOMER_NOT_FOUND");
        // Snapshot the immutable request before checking current plan state, so a retry still replays after plan edits.
        if(repository.findIssueMembershipPlansCount(input.planId())!=1) throw fail(404,"PLAN_NOT_FOUND");
        repository.insertIssueComplimentaryRequests(actor,key,input.customerId(),input.planId(),input.startsAt(),reason);
        var request=repository.findIssueComplimentaryRequestsLocked(actor,key);
        if(!input.customerId().equals(request.get("customer_id")) || !input.planId().equals(request.get("plan_id")) ||
            !input.startsAt().equals((Instant)request.get("starts_at")) || !reason.equals(request.get("reason"))) throw fail(409,"IDEMPOTENCY_CONFLICT");
        if(request.get("grant_id")!=null) return issued((UUID)request.get("grant_id"));
        if(input.startsAt().isBefore(clock.instant().minusSeconds(300)) || input.startsAt().isAfter(clock.instant().plus(Duration.ofDays(366)))) throw fail(400,"INVALID_ACTIVATION_DATE");
        Plan p=plan(input.planId(),true);
        if(!p.active()) throw fail(422,"PLAN_INACTIVE");
        var local=input.startsAt().atZone(ZoneId.of("Asia/Bangkok"));
        Instant until=switch(p.period()) {
            case "MONTH" -> local.plusMonths(1).toInstant();
            case "YEAR" -> local.plusYears(1).toInstant();
            default -> local.plusDays(p.validityDays()).toInstant();
        };
        UUID grant=UUID.randomUUID();
        repository.writeIssueMembershipGrants(grant,input.customerId(),input.startsAt(),until,p.id());
        for(var service:p.serviceIds()) repository.insertIssueGrantServices(grant,service);
        if(p.entitlementMode().equals("CREDITS")) repository.insertIssueCreditLedgerGrant(UUID.randomUUID(),grant,p.credits(),"Complimentary membership grant",clock.instant());
        repository.updateIssueComplimentaryRequestsGrantId(grant,actor,key);
        audit("ISSUE_COMPLIMENTARY",grant,reason);
        return new Issued(grant,until);
    }
    private Issued issued(UUID id) {
        return repository.findIssuedMembershipGrantsIdValidUntil((r,n)->new Issued(r.getObject("id",UUID.class),r.getInstant("valid_until")),id);
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void state(UUID id,GrantState input) {
        var states=repository.findStateMembershipGrantsLocked(id);
        if(states.isEmpty()) throw fail(404,"GRANT_NOT_FOUND");
        if(input.status().equals("ACTIVE") && repository.findStateRefundsCountPending(id)>0)
            throw fail(409,"REFUND_IN_PROGRESS");
        if(states.getFirst().equals("REVOKED") && !input.status().equals("REVOKED")) throw fail(409,"GRANT_REVOKED");
        if(states.getFirst().equals(input.status())) return;
        repository.updateStateMembershipGrantsStatus(input.status(),id);
        audit("MEMBERSHIP_"+input.status(),id,input.reason().strip());
    }
    private void audit(String action,UUID id,String reason) {
        repository.insertAuditAuditEvents(UUID.randomUUID(),Actor.id(),action,id,reason);
    }
}
