package kz.kbtu.sf.botforbusiness.controller;

import kz.kbtu.sf.botforbusiness.dto.BotKnowledgeRequest;
import kz.kbtu.sf.botforbusiness.model.BotKnowledge;
import kz.kbtu.sf.botforbusiness.service.BotKnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bot-knowledge")
public class BotKnowledgeController {

    private final BotKnowledgeService botKnowledgeService;

    public BotKnowledgeController(BotKnowledgeService botKnowledgeService) {
        this.botKnowledgeService = botKnowledgeService;
    }

    @GetMapping
    public ResponseEntity<String> getKnowledge() {
        Long userId = getCurrentUserId();
        return botKnowledgeService
                .getKnowledgeContentByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<BotKnowledge> createKnowledge(@RequestBody BotKnowledgeRequest request) {
        System.out.println("📥 createKnowledge endpoint вызван");
        Long userId = getCurrentUserId();
        BotKnowledge saved = botKnowledgeService.saveKnowledge(userId, request.getSourceType(), request.getContent());
        return ResponseEntity.ok(saved);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PutMapping
    public ResponseEntity<BotKnowledge> updateKnowledge(@RequestBody String newContent) {
        Long userId = getCurrentUserId();
        BotKnowledge updated = botKnowledgeService.updateKnowledgeContent(userId, newContent);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @DeleteMapping
    public ResponseEntity<Void> deleteKnowledge() {
        Long userId = getCurrentUserId();
        botKnowledgeService.deleteKnowledge(userId);
        return ResponseEntity.noContent().build();
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long userId) return userId;
        throw new RuntimeException("User not authenticated");
    }
}
