package kz.kbtu.sf.botforbusiness.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import kz.kbtu.sf.botforbusiness.model.*;
import kz.kbtu.sf.botforbusiness.repository.TelegramRepository;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import kz.kbtu.sf.botforbusiness.repository.WhatsAppRepository;
import org.springframework.stereotype.Service;

@Service
public class BotPlatformService {

    private final TelegramRepository telegramRepository;
    private final WhatsAppRepository whatsAppRepository;
    private final UserRepository userRepository;

    public BotPlatformService(TelegramRepository telegramRepository, WhatsAppRepository whatsAppRepository, UserRepository userRepository) {
        this.telegramRepository = telegramRepository;
        this.whatsAppRepository = whatsAppRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TelegramPlatform createTelegramPlatform(Long userId, String apiToken) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));

        TelegramPlatform platform = new TelegramPlatform(apiToken);
        platform.setOwner(user);

        return telegramRepository.save(platform);
    }

    @Transactional
    public WhatsAppPlatform createWhatsAppPlatform(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));

        WhatsAppPlatform platform = new WhatsAppPlatform();
        platform.setOwner(user);

        return whatsAppRepository.save(platform);
    }

    @Transactional
    public void selectAiModel(Long userId, AiModelType aiModelType) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getAiModel() != aiModelType) {
            user.setAiModel(aiModelType);
            userRepository.save(user);
        }
    }
}
