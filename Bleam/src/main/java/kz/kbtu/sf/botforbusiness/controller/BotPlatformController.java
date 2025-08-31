package kz.kbtu.sf.botforbusiness.controller;

import kz.kbtu.sf.botforbusiness.dto.PlatformRequest;
import kz.kbtu.sf.botforbusiness.model.*;
import kz.kbtu.sf.botforbusiness.service.BotPlatformService;
import kz.kbtu.sf.botforbusiness.service.PlatformServiceFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platforms")
public class BotPlatformController {

    private final PlatformServiceFactory platformServiceFactory;
    private final BotPlatformService botPlatformService;

    public BotPlatformController(PlatformServiceFactory platformServiceFactory, BotPlatformService botPlatformService) {
        this.platformServiceFactory = platformServiceFactory;
        this.botPlatformService = botPlatformService;
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping("/{platformType}/start")
    public ResponseEntity<Void> startPlatform(@PathVariable PlatformType platformType, @RequestBody(required = false) PlatformRequest request) {
        Long userId = getCurrentUserId();
        platformServiceFactory.getService(platformType).startPlatform(userId, request);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping("/{platformType}/stop")
    public ResponseEntity<Void> stopPlatform(@PathVariable PlatformType platformType) {
        Long userId = getCurrentUserId();
        platformServiceFactory.getService(platformType).stopPlatform(userId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping("/{aiModelType}")
    public ResponseEntity<Void> selectAiModel(@PathVariable AiModelType aiModelType) {
        Long userId = getCurrentUserId();
        botPlatformService.selectAiModel(userId, aiModelType);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/aiModelType")
    public AiModelType getAiModelType() {
        Long userId = getCurrentUserId();
        return botPlatformService.getAiModelType(userId);
    }

    @GetMapping("/{platformType}/status")
    public PlatformStatus getPlatformStatus(@PathVariable PlatformType platformType) {
        Long userId = getCurrentUserId();
        return platformServiceFactory.getService(platformType)
                .getPlatformStatus(userId)
                .orElse(null);
    }

    @GetMapping("/whatsapp-ids")
    public List<Long> getAllWhatsAppIds() {
        return platformServiceFactory
                .getService(PlatformType.WHATSAPP)
                .listAllIds();
    }

    @GetMapping("/telegram-ids")
    public List<PlatformRequest> getAllTelegramInfos() {
        return platformServiceFactory
                .getService(PlatformType.TELEGRAM)
                .listAllInfos();
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long userId) return userId;
        throw new RuntimeException("User not authenticated");
    }
}
