package kz.kbtu.sf.botforbusiness.service;

import kz.kbtu.sf.botforbusiness.dto.UserInfo;
import kz.kbtu.sf.botforbusiness.model.*;
import kz.kbtu.sf.botforbusiness.repository.TelegramRepository;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import kz.kbtu.sf.botforbusiness.repository.WhatsAppRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final TelegramRepository telegramRepository;
    private final WhatsAppRepository whatsAppRepository;
    private final TelegramService telegramService;
    private final WhatsAppService whatsAppService;

    public AdminService(UserRepository userRepository, TelegramRepository telegramRepository, WhatsAppRepository whatsAppRepository, TelegramService telegramService, WhatsAppService whatsAppService) {
        this.userRepository = userRepository;
        this.telegramRepository = telegramRepository;
        this.whatsAppRepository = whatsAppRepository;
        this.telegramService = telegramService;
        this.whatsAppService = whatsAppService;
    }

    public String changeRole(Long userId){
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        if (Role.USER == user.getRole()) {
            user.setRole(Role.PENDING);

            telegramRepository.findByOwnerId(userId).ifPresent(telegramPlatform -> {
                if (telegramPlatform.getPlatformStatus() == PlatformStatus.ACTIVE) {
                    telegramService.stopPlatform(userId);
                }
            });

            whatsAppRepository.findByOwnerId(userId).ifPresent(whatsAppPlatform -> {
                if (whatsAppPlatform.getPlatformStatus() == PlatformStatus.ACTIVE) {
                    whatsAppService.stopPlatform(userId);
                }
            });
        }
        else if (Role.PENDING == user.getRole()) {
            user.setRole(Role.USER);
        }
        else {
            throw new RuntimeException("Unsupported role: " + user.getRole());
        }

        userRepository.save(user);
        return user.getRole().toString();
    }

    public List<UserInfo> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> {
                    UserInfo userInfo = new UserInfo();
                    userInfo.setId(user.getId());
                    userInfo.setEmail(user.getEmail());
                    userInfo.setRole(user.getRole());
                    return userInfo;
                })
                .collect(Collectors.toList());
    }
}
