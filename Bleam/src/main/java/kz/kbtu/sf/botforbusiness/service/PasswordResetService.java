package kz.kbtu.sf.botforbusiness.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import kz.kbtu.sf.botforbusiness.model.PasswordResetToken;
import kz.kbtu.sf.botforbusiness.model.User;
import kz.kbtu.sf.botforbusiness.repository.PasswordResetTokenRepository;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final PasswordResetTokenRepository tokenRepo;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final BCryptPasswordEncoder passwordEncoder;

    public PasswordResetService(PasswordResetTokenRepository tokenRepo, UserRepository userRepository, JavaMailSender mailSender, BCryptPasswordEncoder passwordEncoder) {
        this.tokenRepo = tokenRepo;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    public void createAndSendToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Удаляем старые токены
        tokenRepo.findByOwnerId(user.getId()).ifPresent(tokenRepo::delete);

        String token = UUID.randomUUID().toString();
        PasswordResetToken prt = new PasswordResetToken(token, user, LocalDateTime.now().plusMinutes(10));
        tokenRepo.save(prt);

        String resetLink = frontendUrl + "/reset-password?token=" + token;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setSubject("Запрос сброса пароля");
        msg.setText("Вы запросили сброс пароля.\n"
                + "Перейдите по ссылке ниже, чтобы установить новый пароль:\n"
                + resetLink
                + "\n\nСсылка действительна 10 минут.");
        mailSender.send(msg);
    }

    @Transactional
    public void validateToken(String token) {
        PasswordResetToken prt = tokenRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
        if (prt.getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenRepo.delete(prt);
            throw new IllegalArgumentException("Token expired");
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken prt = tokenRepo.findByToken(token).orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (prt.getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenRepo.delete(prt);
            throw new IllegalArgumentException("Token expired");
        }

        User user = prt.getOwner();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepo.delete(prt);
    }
}
