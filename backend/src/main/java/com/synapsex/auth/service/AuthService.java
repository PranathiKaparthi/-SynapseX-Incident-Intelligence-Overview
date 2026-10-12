package com.synapsex.auth.service;

import com.synapsex.auth.dto.*;
import com.synapsex.auth.model.*;
import com.synapsex.auth.repository.*;
import com.synapsex.auth.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("User with email " + req.email() + " already exists");
        }

        String slug = req.workspaceName().toLowerCase().replaceAll("[^a-z0-9]", "-");
        Workspace workspace = workspaceRepository.findBySlug(slug)
                .orElseGet(() -> workspaceRepository.save(new Workspace(null, req.workspaceName(), slug)));

        User user = new User(
                null,
                req.email(),
                passwordEncoder.encode(req.password()),
                req.fullName(),
                SystemRole.ENGINEER
        );
        user = userRepository.save(user);

        WorkspaceMember member = new WorkspaceMember(workspace, user, SystemRole.ENGINEER);
        workspaceMemberRepository.save(member);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), workspace.getId(), member.getRole());
        String rawRefreshToken = jwtService.generateRefreshToken();
        saveRefreshToken(user, rawRefreshToken);

        return new AuthResponse(
                new UserDto(user.getId(), user.getEmail(), user.getFullName(), member.getRole()),
                new WorkspaceDto(workspace.getId(), workspace.getName(), workspace.getSlug()),
                accessToken,
                rawRefreshToken
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId())
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("User does not belong to any workspace"));

        Workspace workspace = member.getWorkspace();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), workspace.getId(), member.getRole());
        String rawRefreshToken = jwtService.generateRefreshToken();
        saveRefreshToken(user, rawRefreshToken);

        return new AuthResponse(
                new UserDto(user.getId(), user.getEmail(), user.getFullName(), member.getRole()),
                new WorkspaceDto(workspace.getId(), workspace.getName(), workspace.getSlug()),
                accessToken,
                rawRefreshToken
        );
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        String tokenHash = jwtService.hashToken(req.refreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new SecurityException("Invalid refresh token"));

        User user = storedToken.getUser();

        // Reuse detection: If a revoked token is used, someone compromised the token stream!
        if (storedToken.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(user.getId());
            throw new SecurityException("Refresh token reuse detected. All active sessions have been invalidated.");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new SecurityException("Refresh token expired");
        }

        // Invalidate the current refresh token
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Find primary workspace membership
        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId())
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("User does not belong to any workspace"));

        Workspace workspace = member.getWorkspace();
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), workspace.getId(), member.getRole());
        String newRawRefreshToken = jwtService.generateRefreshToken();
        saveRefreshToken(user, newRawRefreshToken);

        return new AuthResponse(
                new UserDto(user.getId(), user.getEmail(), user.getFullName(), member.getRole()),
                new WorkspaceDto(workspace.getId(), workspace.getName(), workspace.getSlug()),
                newAccessToken,
                newRawRefreshToken
        );
    }

    @Transactional
    public void logout(UUID userId) {
        if (userId != null) {
            refreshTokenRepository.revokeAllByUserId(userId);
        }
    }

    private void saveRefreshToken(User user, String rawRefreshToken) {
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshExpirationMs());
        RefreshToken entity = new RefreshToken(user, tokenHash, expiresAt);
        refreshTokenRepository.save(entity);
    }
}
