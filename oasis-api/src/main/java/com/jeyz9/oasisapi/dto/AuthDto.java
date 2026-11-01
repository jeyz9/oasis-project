package com.jeyz9.oasisapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDto {
    private AuthDto() {}
    public record Registration(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=12,max=72) String password, @NotBlank @Size(max=100) String displayName) {}
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
}
