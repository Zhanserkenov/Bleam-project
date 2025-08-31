package kz.kbtu.sf.botforbusiness.service;

import jakarta.persistence.EntityNotFoundException;
import kz.kbtu.sf.botforbusiness.dto.PlatformDTO;
import kz.kbtu.sf.botforbusiness.dto.PlatformRequest;
import kz.kbtu.sf.botforbusiness.exception.PlatformOperationException;
import kz.kbtu.sf.botforbusiness.model.*;
import kz.kbtu.sf.botforbusiness.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import kz.kbtu.sf.botforbusiness.repository.WhatsAppRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class WhatsAppService implements PlatformService {

    private final WhatsAppRepository whatsAppRepository;
    private final SessionRepository sessionRepository;
    private final BotPlatformService botPlatformService;
    private final WebClient webClient;

    @Value("${platforms.whatsapp.base-url}")
    private String whatsAppBaseUrl;

    public WhatsAppService(WhatsAppRepository whatsAppRepository,SessionRepository sessionRepository, BotPlatformService botPlatformService, WebClient webClient) {
        this.whatsAppRepository = whatsAppRepository;
        this.sessionRepository = sessionRepository;
        this.botPlatformService = botPlatformService;
        this.webClient = webClient;
    }

    @Override
    @Transactional
    public void startPlatform(Long userId, PlatformRequest platformRequest) {
        WhatsAppPlatform platform = whatsAppRepository.findByOwnerId(userId).orElseGet(() -> botPlatformService.createWhatsAppPlatform(userId));

        try {
            Map<String, Object> request = Map.of("userId", userId);

            String response = webClient.post()
                    .uri(whatsAppBaseUrl + "/start-platform")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            platform.setPlatformStatus(PlatformStatus.ACTIVE);
        } catch (WebClientResponseException ex) {
            log.warn("Unable to launch WhatsApp for user {}: status={}, body={}", userId, ex.getStatusCode(), ex.getResponseBodyAsString());

            throw new PlatformOperationException("Remote service error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception e) {
            log.warn("Unable to launch WhatsApp for user {}: {}", userId, e.getMessage());

            throw new PlatformOperationException("Failed to start WhatsApp platform for user " + userId, e);
        }

        whatsAppRepository.save(platform);
    }

    @Override
    @Transactional
    public void stopPlatform(Long userId) {
        WhatsAppPlatform platform = whatsAppRepository.findByOwnerId(userId).orElseThrow(() -> new EntityNotFoundException("WhatsApp platform not found"));
        try {
            Map<String, Object> request = Map.of("userId", userId);

            String response = webClient.post()
                    .uri(whatsAppBaseUrl + "/stop-platform")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            platform.setPlatformStatus(PlatformStatus.INACTIVE);
        } catch (WebClientResponseException ex) {
            log.warn("Unable to stop WhatsApp for user {}: status={}, body={}", userId, ex.getStatusCode(), ex.getResponseBodyAsString());

            throw new PlatformOperationException("Remote service error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception e) {
            log.warn("Unable to stop WhatsApp for user {}: {}", userId, e.getMessage());

            throw new PlatformOperationException("Failed to stop WhatsApp platform for user " + userId, e);
        }

        whatsAppRepository.save(platform);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> listAllIds() {
        return whatsAppRepository.findAllByStatus(PlatformStatus.ACTIVE)
                .stream()
                .map(WhatsAppPlatform::getId)
                .toList();
    }

    @Override
    public PlatformDTO getPlatformInfo(Long userId) {
        return whatsAppRepository.findByOwnerId(userId)
                .map(platform -> {
                    PlatformDTO dto = new PlatformDTO();
                    dto.setPlatformType(PlatformType.WHATSAPP);
                    dto.setPlatformStatus(platform.getPlatformStatus());
                    return dto;
                }).orElse(null);
    }

    @Override
    public List<Session> getAllSessions(Long userId) {
        return sessionRepository.findByOwnerIdAndPlatformType(userId, PlatformType.WHATSAPP);
    }

    @Override
    public Optional<PlatformStatus> getPlatformStatus(Long userId) {
        return whatsAppRepository.findByOwnerId(userId)
                .map(WhatsAppPlatform::getPlatformStatus);
    }
}
