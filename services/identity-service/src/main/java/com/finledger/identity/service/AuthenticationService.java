package com.finledger.identity.service;

import com.finledger.identity.dto.AuthResponse;
import com.finledger.identity.dto.LoginRequest;
import com.finledger.identity.dto.RefreshTokenRequest;
import com.finledger.identity.dto.RegisterRequest;
import com.finledger.identity.exception.ApiException;
import com.finledger.identity.model.RefreshToken;
import com.finledger.identity.model.User;
import com.finledger.identity.model.UserRole;
import com.finledger.identity.repository.RefreshTokenRepository;
import com.finledger.identity.repository.UserRepository;
import com.finledger.identity.security.JwtProperties;
import com.finledger.identity.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    public AuthenticationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "User already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setRole(UserRole.USER);

        User savedUser = userRepository.save(user);
        return issueTokens(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String incomingToken = request.getRefreshToken();

        try {
            if (!jwtUtil.isTokenValid(incomingToken) || !"refresh".equals(jwtUtil.extractType(incomingToken))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(incomingToken)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token not found"));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return issueTokens(refreshToken.getUser());
    }

    private AuthResponse issueTokens(User user) {
        revokeActiveTokens(user);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail());
        String refreshTokenValue = jwtUtil.generateRefreshToken(user.getEmail());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setRevoked(false);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds()));
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, refreshTokenValue, "Bearer");
    }

    private void revokeActiveTokens(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
        activeTokens.forEach(token -> token.setRevoked(true));
        if (!activeTokens.isEmpty()) {
            refreshTokenRepository.saveAll(activeTokens);
        }
    }
}
