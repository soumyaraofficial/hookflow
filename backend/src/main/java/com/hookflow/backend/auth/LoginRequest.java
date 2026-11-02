package com.hookflow.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LoginRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        String password,

        @NotNull
        UUID tenantId

) {}