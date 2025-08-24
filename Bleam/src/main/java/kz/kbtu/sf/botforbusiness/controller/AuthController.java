package kz.kbtu.sf.botforbusiness.controller;

import jakarta.servlet.http.HttpServletResponse;
import kz.kbtu.sf.botforbusiness.dto.AuthRequest;
import kz.kbtu.sf.botforbusiness.dto.ForgotPasswordRequest;
import kz.kbtu.sf.botforbusiness.dto.ResetPasswordRequest;
import kz.kbtu.sf.botforbusiness.model.User;
import kz.kbtu.sf.botforbusiness.security.JwtUtil;
import kz.kbtu.sf.botforbusiness.service.PasswordResetService;
import kz.kbtu.sf.botforbusiness.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordResetService resetService;

    @Value("${app.oauth2.success-url}")
    private String successUrl;

    @Value("${app.oauth2.error-url}")
    private String errorUrl;

    public AuthController(UserService userService, JwtUtil jwtUtil, PasswordResetService resetService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.resetService = resetService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody AuthRequest request) {
        userService.registerUser(request.getEmail(), request.getPassword());
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody AuthRequest request) {
        User user = userService.loginUser(request.getEmail(), request.getPassword());
        String token = jwtUtil.generateToken(user.getId(), user.getRole().name());
        return ResponseEntity.ok(token);
    }

    @GetMapping("/google/success")
    public void googleSuccess(OAuth2AuthenticationToken authentication, HttpServletResponse response) throws IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (!(auth instanceof OAuth2AuthenticationToken oauth2Token)) {
            response.sendRedirect(errorUrl);
            return;
        }

        String email = authentication.getPrincipal().getAttribute("email");
        User user = userService.findOrCreateGoogleUser(email);
        String token = jwtUtil.generateToken(user.getId(), user.getRole().name());

        response.sendRedirect(successUrl + token);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        resetService.createAndSendToken(request.getEmail());
        return ResponseEntity.ok("Ссылка для сброса отправлена на почту");
    }

    @GetMapping("/validate-reset-token")
    public ResponseEntity<Void> validateToken(@RequestParam("token") String token) {
        try {
            resetService.validateToken(token);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        resetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Пароль успешно изменён");
    }
}
