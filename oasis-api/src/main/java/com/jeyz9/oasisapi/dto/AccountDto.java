package com.jeyz9.oasisapi.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** API contracts; entities never cross HTTP boundaries. */
public final class AccountDto {
    private AccountDto() {}
    public record Principal(UUID id, String email, String displayName, String password, boolean enabled,
                            List<String> roles) implements UserDetails {
        @Override public String getUsername() { return email; }
        @Override public String getPassword() { return password; }
        @Override public boolean isEnabled() { return enabled; }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() {
            return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        }
    }
    public record Me(UUID id, String email, String displayName, List<String> roles) {}
}
