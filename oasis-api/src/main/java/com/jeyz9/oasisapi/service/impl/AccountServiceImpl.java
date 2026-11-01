package com.jeyz9.oasisapi.service.impl;

import com.jeyz9.oasisapi.service.AccountService;
import com.jeyz9.oasisapi.repository.AccountRepository;
import com.jeyz9.oasisapi.dto.AccountDto.Me;
import com.jeyz9.oasisapi.dto.AccountDto.Principal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service
public class AccountServiceImpl implements AccountService {
    private final AccountRepository repository;
    private final PasswordEncoder passwords;
    private final java.time.Clock clock;
    public AccountServiceImpl(AccountRepository repository, PasswordEncoder passwords, java.time.Clock clock) { this.repository = repository; this.passwords = passwords;this.clock=clock; }

    public Me me(Principal p) { return new Me(p.id(), p.email(), p.displayName(), p.roles()); }
    public static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    @Override public Principal loadUserByUsername(String email) {
        return repository.findPrincipal(normalize(email)).orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));
    }
    @Transactional
    public Me register(String email, String password, String displayName) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw fail(400, "PASSWORD_TOO_LONG");
        UUID id = UUID.randomUUID();
        repository.createCustomer(id,normalize(email),passwords.encode(password),displayName.strip(),clock.instant());
        return new Me(id, normalize(email), displayName.strip(), List.of("CUSTOMER"));
    }
}
