package com.jeyz9.oasisapi.repository;

import com.jeyz9.oasisapi.models.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole,UserRole.Key> {
    @Query("select r.role from UserRole r where r.userId=:userId order by r.role")
    List<String> roles(UUID userId);
}
