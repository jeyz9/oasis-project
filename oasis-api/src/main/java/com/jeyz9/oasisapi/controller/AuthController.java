package com.jeyz9.oasisapi.controller;

import com.jeyz9.oasisapi.component.AuthThrottle;
import com.jeyz9.oasisapi.dto.AuthDto.Login;
import com.jeyz9.oasisapi.dto.AuthDto.Registration;
import com.jeyz9.oasisapi.dto.AccountDto;
import com.jeyz9.oasisapi.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import java.time.Clock;
import java.util.Map;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AccountService accounts;
    private final AuthenticationManager authentication;
    private final HttpSessionSecurityContextRepository contextRepository;
    private final HttpSessionCsrfTokenRepository csrfRepository;
    private final AuthThrottle throttle;
    private final Clock clock;
    public AuthController(AccountService accounts, AuthenticationManager authentication, HttpSessionSecurityContextRepository context,
                          HttpSessionCsrfTokenRepository csrf, AuthThrottle throttle, Clock clock) {
        this.accounts = accounts; this.authentication = authentication; contextRepository = context; csrfRepository = csrf; this.throttle = throttle; this.clock=clock;
    }
    @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public AccountDto.Me register(@Valid @RequestBody Registration input, HttpServletRequest q) {
        throttle.check(q.getRemoteAddr());
        return accounts.register(input.email(), input.password(), input.displayName());
    }
    @PostMapping("/auth/login")
    public AccountDto.Me login(@Valid @RequestBody Login input, HttpServletRequest q, HttpServletResponse r) {
        throttle.check(q.getRemoteAddr());
        try {
            var auth = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                AccountService.normalize(input.email()), input.password()));
            // Invalidate the pre-authentication session to rotate ID and reset absolute lifetime.
            var old = q.getSession(false);
            if (old != null) old.invalidate();
            q.getSession(true).setAttribute("oasis.authenticatedAt",clock.millis());
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context,q,r);
            csrfRepository.saveToken(null,q,r);
            return accounts.me((AccountDto.Principal) auth.getPrincipal());
        } catch (AuthenticationException e) { throw fail(401,"INVALID_CREDENTIALS"); }
    }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest q, HttpServletResponse r) {
        var s = q.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
        csrfRepository.saveToken(null,q,r);
    }
    @GetMapping("/me") public AccountDto.Me me(@AuthenticationPrincipal AccountDto.Principal p) { return accounts.me(p); }
}
