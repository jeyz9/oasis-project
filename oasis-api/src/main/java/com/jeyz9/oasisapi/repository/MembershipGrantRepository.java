package com.jeyz9.oasisapi.repository;

import com.jeyz9.oasisapi.models.MembershipGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface MembershipGrantRepository extends JpaRepository<MembershipGrant,UUID> {
    @Query(value="SELECT * FROM membership_grants WHERE id=:id AND customer_id=:customerId FOR NO KEY UPDATE",nativeQuery=true)
    Optional<MembershipGrant> lockOwned(UUID id,UUID customerId);
}
