package com.jeyz9.oasisapi.repository;

import com.jeyz9.oasisapi.models.MembershipPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan,UUID> {}
