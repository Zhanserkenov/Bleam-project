package kz.kbtu.sf.botforbusiness.controller;

import kz.kbtu.sf.botforbusiness.dto.PlatformDTO;
import kz.kbtu.sf.botforbusiness.model.Message;
import kz.kbtu.sf.botforbusiness.model.PlatformType;
import kz.kbtu.sf.botforbusiness.model.Session;
import kz.kbtu.sf.botforbusiness.service.MessageService;
import kz.kbtu.sf.botforbusiness.service.PlatformServiceFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final PlatformServiceFactory platformServiceFactory;
    private final MessageService messageService;

    public SessionController(PlatformServiceFactory platformServiceFactory, MessageService messageService) {
        this.platformServiceFactory = platformServiceFactory;
        this.messageService = messageService;
    }

    @GetMapping
    public List<PlatformDTO> listPlatforms() {
        Long userId = getCurrentUserId();
        return Arrays.stream(PlatformType.values())
                .map(platformType -> platformServiceFactory.getService(platformType).getPlatformInfo(userId))
                .filter(Objects::nonNull)
                .toList();
    }

    @GetMapping("/{platformType}")
    public List<Session> getSessionsByPlatform(@PathVariable PlatformType platformType) {
        Long userId = getCurrentUserId();
        return platformServiceFactory.getService(platformType).getAllSessions(userId);
    }

    @GetMapping("/{sessionId}/messages")
    public List<Message> getSessionMessages(@PathVariable Long sessionId) {
        return messageService.getMessagesForSession(sessionId);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long userId) return userId;
        throw new RuntimeException("User not authenticated");
    }
}
