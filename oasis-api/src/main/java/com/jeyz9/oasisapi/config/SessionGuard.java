package com.jeyz9.oasisapi.config;

import com.jeyz9.oasisapi.dto.AccountDto;
import com.jeyz9.oasisapi.service.AccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

// Refresh roles/disabled status each request; enforce an absolute lifetime in addition to idle expiry.
final class SessionGuard extends OncePerRequestFilter {
    private final AccountService accounts;
    private final Clock clock;
    SessionGuard(AccountService accounts, Clock clock) { this.accounts = accounts; this.clock = clock; }
    @Override protected void doFilterInternal(HttpServletRequest q, HttpServletResponse r, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountDto.Principal p) {
            var session = q.getSession(false);
            Object authenticatedAt=session==null?null:session.getAttribute("oasis.authenticatedAt");
            boolean expired = !(authenticatedAt instanceof Long at) || clock.millis() - at >= Duration.ofHours(8).toMillis();
            try {
                var current = accounts.loadUserByUsername(p.email());
                if (expired || !current.enabled()) {
                    if (session != null) session.invalidate();
                    SecurityContextHolder.clearContext();
                } else {
                    SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(current, null, current.getAuthorities()));
                }
            } catch (UsernameNotFoundException e) {
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(q,r);
    }
}
