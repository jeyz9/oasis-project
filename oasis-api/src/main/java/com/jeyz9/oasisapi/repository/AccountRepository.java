package com.jeyz9.oasisapi.repository;

import com.jeyz9.oasisapi.models.User;
import com.jeyz9.oasisapi.models.UserRole;
import com.jeyz9.oasisapi.dto.AccountDto.Principal;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository @Transactional
public class AccountRepository {
    private final UserRepository users;
    private final UserRoleRepository roles;
    public AccountRepository(UserRepository users,UserRoleRepository roles) { this.users=users;this.roles=roles; }
    @Transactional(readOnly=true)
    public Optional<Principal> findPrincipal(String email) {
        return users.findByEmail(email).map(u->new Principal(u.getId(),u.getEmail(),u.getDisplayName(),u.getPasswordHash(),u.getEnabled(),roles.roles(u.getId())));
    }
    public void createCustomer(UUID id,String email,String hash,String name,Instant now) {
        var user=new User();user.setId(id);user.setEmail(email);user.setPasswordHash(hash);user.setDisplayName(name);user.setEnabled(true);user.setCreatedAt(now);
        users.saveAndFlush(user);
        var role=new UserRole();role.setUserId(id);role.setRole("CUSTOMER");roles.saveAndFlush(role);
    }
}
