package com.jeyz9.oasisapi.controller;
import com.jeyz9.oasisapi.dto.MembershipDto;
import com.jeyz9.oasisapi.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1")
public class MembershipController {
    private final MembershipService memberships;
    public MembershipController(MembershipService memberships) { this.memberships=memberships; }
    @GetMapping("/membership-plans") public List<MembershipDto.Plan> catalogue() { return memberships.catalogue(); }
    @GetMapping("/admin/membership-plans") public List<MembershipDto.Plan> plans() { return memberships.all(); }
    @PostMapping("/admin/membership-plans") @ResponseStatus(HttpStatus.CREATED)
    public MembershipDto.Plan create(@Valid @RequestBody MembershipDto.PlanInput input) { return memberships.save(null,input); }
    @PutMapping("/admin/membership-plans/{id}") public MembershipDto.Plan update(@PathVariable UUID id,@Valid @RequestBody MembershipDto.PlanInput input) { return memberships.save(id,input); }
    @GetMapping("/admin/customers") public List<MembershipDto.Customer> customers() { return memberships.customers(); }
    @GetMapping("/admin/memberships") public List<MembershipDto.AdminGrant> grants() { return memberships.grants(); }
    @PostMapping("/admin/memberships/complimentary") @ResponseStatus(HttpStatus.CREATED)
    public MembershipDto.Issued complimentary(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody MembershipDto.Complimentary input) { return memberships.issue(key,input); }
    @PutMapping("/admin/memberships/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void state(@PathVariable UUID id,@Valid @RequestBody MembershipDto.GrantState input) { memberships.state(id,input); }
}
