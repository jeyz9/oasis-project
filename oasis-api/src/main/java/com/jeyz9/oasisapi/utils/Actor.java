package com.jeyz9.oasisapi.utils;

import com.jeyz9.oasisapi.dto.AccountDto;
import com.jeyz9.oasisapi.component.ApiError;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

public final class Actor {
    private Actor() {}
    public static UUID id() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AccountDto.Principal p)) throw ApiError.fail(401,"UNAUTHENTICATED");
        return p.id();
    }
}
