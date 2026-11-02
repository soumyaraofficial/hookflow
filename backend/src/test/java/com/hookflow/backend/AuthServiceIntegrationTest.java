package com.hookflow.backend;

import com.hookflow.backend.auth.AuthService;
import com.hookflow.backend.auth.LoginRequest;
import com.hookflow.backend.auth.LoginResponse;
import com.hookflow.backend.auth.RefreshToken;
import com.hookflow.backend.auth.RefreshTokenRepository;
import com.hookflow.backend.auth.RefreshTokenRequest;
import com.hookflow.backend.auth.RegisterRequest;
import com.hookflow.backend.auth.RegisterResponse;
import com.hookflow.backend.auth.TokenService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthServiceIntegrationTest {

    private static final String PASSWORD = "test-password-123";

    @Autowired AuthService authService;
    @Autowired TokenService tokenService;
    @Autowired RefreshTokenRepository refreshTokens;

    private String newEmail() {
        return "auth-" + UUID.randomUUID() + "@example.com";
    }

    private RegisterResponse register(String email) {
        return authService.register(new RegisterRequest(email, PASSWORD, "Tenant " + UUID.randomUUID()));
    }

    private LoginResponse login(String email, UUID tenantId) {
        return authService.login(new LoginRequest(email, PASSWORD, tenantId));
    }

    private static void assertUnauthorized(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginReturnsTokensAndStoresOnlyTheHashOfTheRefreshToken() {
        String email = newEmail();
        RegisterResponse registered = register(email);

        LoginResponse response = login(email, registered.tenantId());

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.tenantId()).isEqualTo(registered.tenantId());

        String hash = tokenService.hashRefreshToken(response.refreshToken());
        assertThat(hash).isNotEqualTo(response.refreshToken());
        assertThat(refreshTokens.findByTokenHash(hash)).isPresent();
        assertThat(refreshTokens.findByTokenHash(response.refreshToken())).isEmpty();
    }

    @Test
    void loginRejectsWrongPassword() {
        String email = newEmail();
        RegisterResponse registered = register(email);

        assertUnauthorized(() -> authService.login(
                new LoginRequest(email, "wrong-password-123", registered.tenantId())));
    }

    @Test
    void loginRejectsUnknownEmail() {
        assertUnauthorized(() -> login(newEmail(), UUID.randomUUID()));
    }

    @Test
    void loginRejectsTenantTheUserDoesNotBelongTo() {
        String email = newEmail();
        register(email);
        RegisterResponse other = register(newEmail());

        assertUnauthorized(() -> login(email, other.tenantId()));
    }

    @Test
    void refreshRotatesTheTokenAndRejectsReuseOfTheOldOne() {
        String email = newEmail();
        RegisterResponse registered = register(email);
        LoginResponse first = login(email, registered.tenantId());

        LoginResponse second = authService.refresh(new RefreshTokenRequest(first.refreshToken()));

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.tenantId()).isEqualTo(registered.tenantId());
        assertThat(refreshTokens.findByTokenHash(
                tokenService.hashRefreshToken(first.refreshToken())).orElseThrow().getRevokedAt())
                .isNotNull();

        assertUnauthorized(() -> authService.refresh(new RefreshTokenRequest(first.refreshToken())));
        // the new token still works
        authService.refresh(new RefreshTokenRequest(second.refreshToken()));
    }

    @Test
    void refreshRejectsExpiredToken() {
        String email = newEmail();
        RegisterResponse registered = register(email);
        String raw = tokenService.generateRefreshToken();

        refreshTokens.saveAndFlush(new RefreshToken(
                registered.userId(),
                registered.tenantId(),
                tokenService.hashRefreshToken(raw),
                Instant.now().minusSeconds(60)));

        assertUnauthorized(() -> authService.refresh(new RefreshTokenRequest(raw)));
    }

    @Test
    void logoutRevokesTokenAndIsIdempotent() {
        String email = newEmail();
        RegisterResponse registered = register(email);
        LoginResponse response = login(email, registered.tenantId());
        RefreshTokenRequest request = new RefreshTokenRequest(response.refreshToken());

        authService.logout(request);
        authService.logout(request); // second call must not fail

        assertUnauthorized(() -> authService.refresh(request));
    }
}