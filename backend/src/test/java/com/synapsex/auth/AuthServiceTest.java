package com.synapsex.auth;

import com.synapsex.auth.dto.*;
import com.synapsex.auth.model.*;
import com.synapsex.auth.repository.*;
import com.synapsex.auth.security.JwtService;
import com.synapsex.auth.security.UserPrincipal;
import com.synapsex.auth.security.WorkspaceSecurityService;
import com.synapsex.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthService authService;
    private WorkspaceSecurityService workspaceSecurityService;

    private User sampleUser;
    private Workspace sampleWorkspace;
    private WorkspaceMember sampleMember;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                workspaceRepository,
                workspaceMemberRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService
        );
        workspaceSecurityService = new WorkspaceSecurityService();

        sampleWorkspace = new Workspace(UUID.randomUUID(), "Platform Team", "platform-team");
        sampleUser = new User(UUID.randomUUID(), "alice@synapsex.io", "hashedPass", "Alice Vance", SystemRole.ENGINEER);
        sampleMember = new WorkspaceMember(sampleWorkspace, sampleUser, SystemRole.ENGINEER);
    }

    @Test
    @DisplayName("Should register new user, create workspace, and issue tokens")
    void testRegisterSuccess() {
        RegisterRequest req = new RegisterRequest("alice@synapsex.io", "Password123!", "Alice Vance", "Platform Team");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(workspaceRepository.findBySlug(anyString())).thenReturn(Optional.of(sampleWorkspace));
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("access.token.jwt");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken(anyString())).thenReturn("hashed-refresh-token");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);

        AuthResponse resp = authService.register(req);

        assertNotNull(resp);
        assertEquals("alice@synapsex.io", resp.user().email());
        assertEquals("access.token.jwt", resp.accessToken());
        assertEquals("raw-refresh-token", resp.refreshToken());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void testLoginSuccess() {
        LoginRequest req = new LoginRequest("alice@synapsex.io", "Password123!");

        when(userRepository.findByEmail("alice@synapsex.io")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password123!", "hashedPass")).thenReturn(true);
        when(workspaceMemberRepository.findByUserId(sampleUser.getId())).thenReturn(List.of(sampleMember));
        when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("valid.access.jwt");
        when(jwtService.generateRefreshToken()).thenReturn("valid.refresh");
        when(jwtService.hashToken(anyString())).thenReturn("hashed.refresh");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);

        AuthResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("valid.access.jwt", resp.accessToken());
        assertEquals("valid.refresh", resp.refreshToken());
    }

    @Test
    @DisplayName("Should rotate refresh token on valid refresh request")
    void testRefreshTokenRotation() {
        RefreshRequest req = new RefreshRequest("valid-old-token");
        String oldHash = "hash-old-token";
        RefreshToken existingToken = new RefreshToken(sampleUser, oldHash, Instant.now().plusSeconds(3600));

        when(jwtService.hashToken("valid-old-token")).thenReturn(oldHash);
        when(refreshTokenRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(existingToken));
        when(workspaceMemberRepository.findByUserId(sampleUser.getId())).thenReturn(List.of(sampleMember));
        when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("new.access.token");
        when(jwtService.generateRefreshToken()).thenReturn("new.refresh.token");
        when(jwtService.hashToken("new.refresh.token")).thenReturn("hash-new-token");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);

        AuthResponse resp = authService.refresh(req);

        assertNotNull(resp);
        assertTrue(existingToken.isRevoked(), "Old refresh token must be revoked during rotation");
        assertEquals("new.access.token", resp.accessToken());
        assertEquals("new.refresh.token", resp.refreshToken());
    }

    @Test
    @DisplayName("Reuse Detection: presenting revoked refresh token must revoke all user tokens")
    void testRefreshTokenReuseDetection() {
        RefreshRequest req = new RefreshRequest("stolen-revoked-token");
        String hash = "hash-stolen-token";
        RefreshToken compromisedToken = new RefreshToken(sampleUser, hash, Instant.now().plusSeconds(3600));
        compromisedToken.setRevoked(true); // Already used previously!

        when(jwtService.hashToken("stolen-revoked-token")).thenReturn(hash);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(compromisedToken));

        SecurityException ex = assertThrows(SecurityException.class, () -> authService.refresh(req));
        assertTrue(ex.getMessage().contains("reuse detected"), "Exception message must report reuse detection");

        // Verify that ALL sessions for this user were invalidated
        verify(refreshTokenRepository, times(1)).revokeAllByUserId(sampleUser.getId());
    }

    @Test
    @DisplayName("Workspace Isolation: user in Workspace A cannot access Workspace B")
    void testWorkspaceIsolationEnforcement() {
        UUID workspaceA = UUID.randomUUID();
        UUID workspaceB = UUID.randomUUID();

        UserPrincipal principalUserA = new UserPrincipal(UUID.randomUUID(), "alice@synapsex.io", workspaceA, SystemRole.ENGINEER);
        UsernamePasswordAuthenticationToken authA =
                new UsernamePasswordAuthenticationToken(principalUserA, null, principalUserA.getAuthorities());

        // Same workspace -> allowed
        assertTrue(workspaceSecurityService.canAccess(authA, workspaceA), "Engineer should access own workspace");

        // Different workspace -> FORBIDDEN
        assertFalse(workspaceSecurityService.canAccess(authA, workspaceB), "Engineer must be denied access to Workspace B");

        // System Admin can access any workspace
        UserPrincipal principalAdmin = new UserPrincipal(UUID.randomUUID(), "admin@synapsex.io", workspaceA, SystemRole.ADMIN);
        UsernamePasswordAuthenticationToken authAdmin =
                new UsernamePasswordAuthenticationToken(principalAdmin, null, principalAdmin.getAuthorities());
        assertTrue(workspaceSecurityService.canAccess(authAdmin, workspaceB), "Admin can access all workspaces");
    }
}
