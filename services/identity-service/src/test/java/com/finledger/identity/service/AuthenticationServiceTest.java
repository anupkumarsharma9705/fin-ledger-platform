package com.finledger.identity.service;

import com.finledger.identity.dto.AuthResponse;
import com.finledger.identity.dto.LoginRequest;
import com.finledger.identity.dto.RefreshTokenRequest;
import com.finledger.identity.dto.RegisterRequest;
import com.finledger.identity.exception.ApiException;
import com.finledger.identity.model.RefreshToken;
import com.finledger.identity.model.User;
import com.finledger.identity.repository.RefreshTokenRepository;
import com.finledger.identity.repository.UserRepository;
import com.finledger.identity.security.JwtProperties;
import com.finledger.identity.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setRefreshTokenExpirationSeconds(3600);
        authenticationService = new AuthenticationService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtUtil,
                jwtProperties
        );
    }

    @Test
    void registerShouldCreateUserAndTokens() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setFullName("Test User");

        User savedUser = new User();
        savedUser.setEmail("test@example.com");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(savedUser)).thenReturn(List.of());
        when(jwtUtil.generateAccessToken("test@example.com")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("test@example.com")).thenReturn("refresh-token");

        AuthResponse response = authenticationService.register(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    void registerShouldFailWhenUserExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("exists@example.com");
        when(userRepository.existsByEmail("exists@example.com")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> authenticationService.register(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void loginShouldFailWithInvalidCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrong-password");

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("encoded");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> authenticationService.login(request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void refreshTokenShouldFailForUnknownToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("unknown");

        when(jwtUtil.isTokenValid("unknown")).thenReturn(true);
        when(jwtUtil.extractType("unknown")).thenReturn("refresh");
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> authenticationService.refreshToken(request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void refreshTokenShouldRotateTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-token");

        User user = new User();
        user.setEmail("refresh@example.com");

        RefreshToken existing = new RefreshToken();
        existing.setToken("valid-token");
        existing.setUser(user);
        existing.setRevoked(false);
        existing.setExpiresAt(Instant.now().plusSeconds(60));

        when(jwtUtil.isTokenValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractType("valid-token")).thenReturn("refresh");
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());
        when(jwtUtil.generateAccessToken("refresh@example.com")).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken("refresh@example.com")).thenReturn("new-refresh");

        AuthResponse response = authenticationService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new-access", response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());
    }
}
