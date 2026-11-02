package com.hookflow.backend.auth;

import com.hookflow.backend.tenant.Tenant;
import com.hookflow.backend.tenant.TenantMembership;
import com.hookflow.backend.tenant.TenantMembershipRepository;
import com.hookflow.backend.tenant.TenantRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String INVALID_REFRESH = "Invalid refresh token";
    private static final long ACCESS_TOKEN_MINUTES = 15;
    private static final long REFRESH_TOKEN_DAYS = 30;

    private final TokenService tokenService;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;

    public AuthService(
            UserRepository users,
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            TokenService tokenService
    ) {
        this.users = users;
        this.tenants = tenants;
        this.memberships = memberships;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw conflict();
        }

        User user;
        try {
            user = users.saveAndFlush(
                    new User(email, passwordEncoder.encode(request.password()))
            );
        } catch (DataIntegrityViolationException exception) {
            // two requests registered the same email at the same moment
            throw conflict();
        }

        Tenant tenant = tenants.saveAndFlush(
                new Tenant(request.tenantName().trim())
        );

        TenantMembership membership = memberships.saveAndFlush(
                new TenantMembership(tenant.getId(), user.getId(), "ADMIN")
        );

        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                tenant.getId(),
                tenant.getName(),
                membership.getRole()
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> unauthorized(INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())
                || !"ACTIVE".equals(user.getStatus())) {
            throw unauthorized(INVALID_CREDENTIALS);
        }

        return issueTokens(user, request.tenantId(), INVALID_CREDENTIALS);
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokens
                .findForUpdateByTokenHash(
                        tokenService.hashRefreshToken(request.refreshToken()))
                .orElseThrow(() -> unauthorized(INVALID_REFRESH));

        if (stored.getRevokedAt() != null
                || !stored.getExpiresAt().isAfter(Instant.now())) {
            throw unauthorized(INVALID_REFRESH);
        }

        User user = users.findById(stored.getUserId())
                .filter(found -> "ACTIVE".equals(found.getStatus()))
                .orElseThrow(() -> unauthorized(INVALID_REFRESH));

        stored.revoke();
        refreshTokens.save(stored);

        return issueTokens(user, stored.getTenantId(), INVALID_REFRESH);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokens
                .findByTokenHash(tokenService.hashRefreshToken(request.refreshToken()))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokens.save(token);
                });
    }

    private LoginResponse issueTokens(User user, UUID tenantId, String failureMessage) {
        Tenant tenant = tenants.findById(tenantId)
                .filter(found -> "ACTIVE".equals(found.getStatus()))
                .orElseThrow(() -> unauthorized(failureMessage));

        TenantMembership membership = memberships
                .findByTenantIdAndUserId(tenant.getId(), user.getId())
                .filter(found -> "ACTIVE".equals(found.getStatus()))
                .orElseThrow(() -> unauthorized(failureMessage));

        Instant now = Instant.now();
        String accessToken = tokenService.generateAccessToken(user, tenant, membership);
        String refreshToken = tokenService.generateRefreshToken();

        refreshTokens.save(new RefreshToken(
                user.getId(),
                tenant.getId(),
                tokenService.hashRefreshToken(refreshToken),
                now.plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS)
        ));

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                now.plus(ACCESS_TOKEN_MINUTES, ChronoUnit.MINUTES),
                user.getId(),
                tenant.getId(),
                membership.getRole()
        );
    }

    private static ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Email is already registered");
    }
}