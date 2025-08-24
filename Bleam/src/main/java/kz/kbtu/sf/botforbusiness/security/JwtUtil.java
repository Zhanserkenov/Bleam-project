package kz.kbtu.sf.botforbusiness.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret.user}")
    private String jwtUserSecret;

    @Value("${jwt.secret.service}")
    private String jwtServiceSecret;

    @Value("${jwt.expiration.user}")
    private long jwtUserExpirationMs;

    @Value("${jwt.expiration.service}")
    private long jwtServiceExpirationMs;

    private Key userKey() {
        return Keys.hmacShaKeyFor(jwtUserSecret.getBytes(StandardCharsets.UTF_8));
    }

    private Key serviceKey() {
        return Keys.hmacShaKeyFor(jwtServiceSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String roleClaim) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuer("my-spring-app")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtUserExpirationMs))
                .claim("token_type", "user")
                .claim("role", roleClaim == null ? "PENDING" : roleClaim)
                .signWith(userKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(Long userId) {
        return generateToken(userId, "USER");
    }

    public String generateServiceToken(String serviceName) {
        return Jwts.builder()
                .setSubject(serviceName)
                .setIssuer("my-spring-app")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtServiceExpirationMs))
                .claim("token_type", "service")
                .claim("service", serviceName)
                .claim("role", "SERVICE")
                .signWith(serviceKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims getClaims(String token, boolean isServiceToken) {
        Key key = isServiceToken ? serviceKey() : userKey();
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token, boolean isServiceToken) {
        try {
            Key key = isServiceToken ? serviceKey() : userKey();
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isValidServiceToken(String token) {
        try {
            Claims claims = getClaims(token, true);
            String type = claims.get("token_type", String.class);
            return "service".equals(type);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
