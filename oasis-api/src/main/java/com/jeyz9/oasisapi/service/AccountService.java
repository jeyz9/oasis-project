package com.jeyz9.oasisapi.service;

import org.springframework.security.core.userdetails.UserDetailsService;
import java.util.Locale;
import com.jeyz9.oasisapi.dto.AccountDto.Me;
import com.jeyz9.oasisapi.dto.AccountDto.Principal;

public interface AccountService extends UserDetailsService {
    static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    Me me(Principal p);
    Principal loadUserByUsername(String email);
    Me register(String email, String password, String displayName);
}
