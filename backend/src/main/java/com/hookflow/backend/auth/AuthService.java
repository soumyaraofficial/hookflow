package com.hookflow.backend.auth;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.hookflow.backend.tenant.Tenant;
import com.hookflow.backend.tenant.TenantMembership;
import com.hookflow.backend.tenant.TenantMembershipRepository;
import com.hookflow.backend.tenant.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
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
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())
                || !"ACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        Tenant tenant = tenants.findById(request.tenantId())
                .filter(foundTenant -> "ACTIVE".equals(foundTenant.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        TenantMembership membership = memberships
                .findByTenantIdAndUserId(tenant.getId(), user.getId())
                .filter(foundMembership ->
                        "ACTIVE".equals(foundMembership.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        String accessToken =
                tokenService.generateAccessToken(user, tenant, membership);

        String refreshToken = tokenService.generateRefreshToken();
        Instant refreshTokenExpiresAt = Instant.now().plus(30, ChronoUnit.DAYS);

        refreshTokens.save(new RefreshToken(
                user.getId(),
                tokenService.hashRefreshToken(refreshToken),
                refreshTokenExpiresAt
        ));

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                Instant.now().plus(15, ChronoUnit.MINUTES),
                user.getId(),
                tenant.getId(),
                membership.getRole()
        );
    }
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        User user = users.saveAndFlush(
                new User(email, passwordEncoder.encode(request.password()))
        );

        Tenant tenant = tenants.saveAndFlush(
                new Tenant(request.tenantName().trim())
        );

        TenantMembership membership = memberships.saveAndFlush(
                new TenantMembership(
                        tenant.getId(),
                        user.getId(),
                        "ADMIN"
                )
        );

        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                tenant.getId(),
                tenant.getName(),
                membership.getRole()
        );
    }


}