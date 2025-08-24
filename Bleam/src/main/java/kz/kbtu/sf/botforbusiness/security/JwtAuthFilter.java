package kz.kbtu.sf.botforbusiness.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtil.validateToken(token, false)) {
                Claims claims = jwtUtil.getClaims(token, false);
                String subject = claims.getSubject(); // в твоём коде это userId
                Long userId = null;
                try {
                    userId = Long.parseLong(subject);
                } catch (NumberFormatException e) {
                    log.warn("User subject is not numeric: {}", subject);
                }

                String roleClaim = claims.get("role", String.class);
                if (roleClaim == null || roleClaim.isBlank()) {
                    roleClaim = "PENDING"; // fallback
                }

                var authority = new SimpleGrantedAuthority("ROLE_" + roleClaim.trim());

                if (userId != null) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId, // principal
                            null,
                            List.of(authority)
                    );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } else if (jwtUtil.validateToken(token, true)) {
                Claims claims = jwtUtil.getClaims(token, true);
                String serviceName = claims.get("service", String.class);
                if (serviceName == null || serviceName.isBlank()) {
                    serviceName = claims.getSubject(); // fallback на sub
                }

                String roleClaim = claims.get("role", String.class);
                if (roleClaim == null || roleClaim.isBlank()) {
                    roleClaim = "SERVICE";
                }
                var authority = new SimpleGrantedAuthority("ROLE_" + roleClaim.trim());

                var auth = new UsernamePasswordAuthenticationToken(
                        serviceName,
                        null,
                        List.of(authority)
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
            }
        } catch (Exception ex) {
            log.debug("Token validation error: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String accessToken = request.getParameter("access_token");
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken;
        }

        return null;
    }
}