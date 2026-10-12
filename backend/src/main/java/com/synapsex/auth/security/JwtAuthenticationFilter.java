package com.synapsex.auth.security;

import com.synapsex.auth.model.SystemRole;
import com.synapsex.workspace.WorkspaceContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtService.validateToken(jwt)) {
                Claims claims = jwtService.parseClaims(jwt);
                UUID userId = UUID.fromString(claims.getSubject());
                String email = claims.get("email", String.class);
                String wsIdStr = claims.get("workspaceId", String.class);
                UUID workspaceId = wsIdStr != null ? UUID.fromString(wsIdStr) : null;
                String roleStr = claims.get("role", String.class);
                SystemRole role = roleStr != null ? SystemRole.valueOf(roleStr) : SystemRole.ENGINEER;

                UserPrincipal principal = new UserPrincipal(userId, email, workspaceId, role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                if (workspaceId != null) {
                    WorkspaceContextHolder.setWorkspaceId(workspaceId);
                    MDC.put("workspaceId", workspaceId.toString());
                }
                MDC.put("userId", userId.toString());
            }

            filterChain.doFilter(request, response);
        } finally {
            WorkspaceContextHolder.clear();
            MDC.remove("workspaceId");
            MDC.remove("userId");
        }
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
