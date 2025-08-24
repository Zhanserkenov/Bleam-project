package kz.kbtu.sf.botforbusiness.security;

import java.security.Principal;
import java.util.Map;

import io.jsonwebtoken.Claims;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import jakarta.servlet.http.HttpServletRequest;

public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    private final JwtUtil jwtUtil;

    public JwtHandshakeHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        String token = httpRequest.getParameter("access_token");
        if (token == null) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        if (token == null) return null;

        try {
            if (!jwtUtil.validateToken(token, false)) {
                return null;
            }

            Claims claims = jwtUtil.getClaims(token, false);
            String subject = claims.getSubject();
            if (subject == null) return null;

            Long userId;
            try {
                userId = Long.parseLong(subject);
            } catch (NumberFormatException e) {
                return null;
            }

            final String name = String.valueOf(userId);
            return new Principal() {
                @Override
                public String getName() {
                    return name;
                }
            };
        } catch (Exception e) {
            return null;
        }
    }
}