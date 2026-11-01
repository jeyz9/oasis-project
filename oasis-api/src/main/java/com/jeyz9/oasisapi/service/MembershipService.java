package com.jeyz9.oasisapi.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;
import com.jeyz9.oasisapi.dto.MembershipDto.AdminGrant;
import com.jeyz9.oasisapi.dto.MembershipDto.Complimentary;
import com.jeyz9.oasisapi.dto.MembershipDto.Customer;
import com.jeyz9.oasisapi.dto.MembershipDto.GrantState;
import com.jeyz9.oasisapi.dto.MembershipDto.Issued;
import com.jeyz9.oasisapi.dto.MembershipDto.Plan;
import com.jeyz9.oasisapi.dto.MembershipDto.PlanInput;

public interface MembershipService {
    List<Plan> catalogue();
    List<Plan> all();
    Plan save(UUID id,@Valid PlanInput input);
    List<Customer> customers();
    List<AdminGrant> grants();
    Issued issue(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{8,80}") String key,@Valid Complimentary input);
    void state(UUID id,@Valid GrantState input);
}
