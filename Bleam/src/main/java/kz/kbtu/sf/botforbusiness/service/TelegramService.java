package kz.kbtu.sf.botforbusiness.service;

import jakarta.persistence.EntityNotFoundException;
import kz.kbtu.sf.botforbusiness.dto.PlatformDTO;
import kz.kbtu.sf.botforbusiness.dto.PlatformRequest;
import kz.kbtu.sf.botforbusiness.exception.PlatformOperationException;
import kz.kbtu.sf.botforbusiness.model.PlatformType;
import kz.kbtu.sf.botforbusiness.model.Session;
import kz.kbtu.sf.botforbusiness.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import kz.kbtu.sf.botforbusiness.model.PlatformStatus;
import kz.kbtu.sf.botforbusiness.model.TelegramPlatform;
import kz.kbtu.sf.botforbusiness.repository.TelegramRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class TelegramService implements PlatformService {

    private final TelegramRepository telegramRepository;
    private final SessionRepository sessionRepository;
    private final BotPlatformService botPlatformService;
    private final WebClient webClient;

    @Value("${platforms.telegram.base-url}")
    private String telegramBaseUrl;

    public TelegramService(TelegramRepository telegramRepository, SessionRepository sessionRepository, BotPlatformService botPlatformService, WebClient webClient) {
        this.telegramRepository = telegramRepository;
        this.sessionRepository = sessionRepository;
        this.botPlatformService = botPlatformService;
        this.webClient = webClient;
    }

    @Override
    @Transactional
    public void startPlatform(Long userId, PlatformRequest platformRequest) {
        TelegramPlatform platform = telegramRepository.findByOwnerId(userId).orElseGet(() -> {
            if (platformRequest == null) {
                throw new IllegalArgumentException("apiToken must be provided");
            }
            return botPlatformService.createTelegramPlatform(userId, platformRequest.getApiToken());
        });

        if (!Objects.equals(platformRequest.getApiToken(), platform.getApiToken()) && platform.getPlatformStatus() == PlatformStatus.INACTIVE) {
            platform.setApiToken(platformRequest.getApiToken());
        }

        try {
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "apiToken", platform.getApiToken()
            );

            String response = webClient.post()
                    .uri(telegramBaseUrl + "/start-platform")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            platform.setPlatformStatus(PlatformStatus.ACTIVE);
        } catch (WebClientResponseException ex) {
            log.warn("Unable to launch Telegram for user {}: status={}, body={}", userId, ex.getStatusCode(), ex.getResponseBodyAsString());

            throw new PlatformOperationException("Remote service error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception e) {
            log.warn("Unable to launch Telegram for user {}: {}", userId, e.getMessage());

            throw new PlatformOperationException("Failed to start platform for user " + userId, e);
        }

        telegramRepository.save(platform);
    }

    @Override
    @Transactional
    public void stopPlatform(Long userId) {
        TelegramPlatform platform = telegramRepository.findByOwnerId(userId).orElseThrow(() -> new EntityNotFoundException("Telegram platform not found"));

        try {
            Map<String, Object> request = Map.of("userId", userId);

            String response = webClient.post()
                    .uri(telegramBaseUrl + "/stop-platform")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            platform.setPlatformStatus(PlatformStatus.INACTIVE);
        } catch (WebClientResponseException ex) {
            log.warn("Unable to stop Telegram for user {}: status={}, body={}", userId, ex.getStatusCode(), ex.getResponseBodyAsString());

            throw new PlatformOperationException("Remote service error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception e) {
            log.warn("Unable to stop Telegram for user {}: {}", userId, e.getMessage());

            throw new PlatformOperationException("Failed to stop platform for user " + userId, e);
        }

        telegramRepository.save(platform);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformRequest> listAllInfos() {
        return telegramRepository.findAllByStatus(PlatformStatus.ACTIVE)
                .stream()
                .map(p -> new PlatformRequest(
                        p.getId(),
                        p.getApiToken()
                ))
                .toList();
    }

    @Override
    public PlatformDTO getPlatformInfo(Long userId) {
        return telegramRepository.findByOwnerId(userId)
                .map(platform -> {
                    PlatformDTO dto = new PlatformDTO();
                    dto.setPlatformType(PlatformType.TELEGRAM);
                    dto.setPlatformStatus(platform.getPlatformStatus());
                    return dto;
                }).orElse(null);
    }

    @Override
    public List<Session> getAllSessions(Long userId) {
        return sessionRepository.findByOwnerIdAndPlatformType(userId, PlatformType.TELEGRAM);
    }
}
