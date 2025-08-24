package kz.kbtu.sf.botforbusiness.service;

import jakarta.persistence.EntityNotFoundException;
import kz.kbtu.sf.botforbusiness.model.BotKnowledge;
import kz.kbtu.sf.botforbusiness.model.SourceType;
import kz.kbtu.sf.botforbusiness.model.User;
import kz.kbtu.sf.botforbusiness.repository.BotKnowledgeRepository;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BotKnowledgeService {

    private final BotKnowledgeRepository botKnowledgeRepository;
    private final UserRepository userRepository;

    public BotKnowledgeService(BotKnowledgeRepository botKnowledgeRepository, UserRepository userRepository) {
        this.botKnowledgeRepository = botKnowledgeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BotKnowledge saveKnowledge(Long userId, SourceType sourceType, String content) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (botKnowledgeRepository.findByOwnerId(userId).isPresent()) {
            throw new SecurityException("You already set the knowledge");
        }

        BotKnowledge knowledge = new BotKnowledge(sourceType, content);
        knowledge.setOwner(user);

        return botKnowledgeRepository.save(knowledge);
    }

    @Transactional
    public void deleteKnowledge(Long userId) {
        BotKnowledge knowledge = botKnowledgeRepository.findByOwnerId(userId).orElseThrow(() -> new EntityNotFoundException("Knowledge not found"));
        botKnowledgeRepository.delete(knowledge);
    }

    @Transactional
    public BotKnowledge updateKnowledgeContent(Long userId, String newContent) {
        BotKnowledge knowledge = botKnowledgeRepository.findByOwnerId(userId).orElseThrow(() -> new EntityNotFoundException("Knowledge not found"));

        if (knowledge.getSourceType() != SourceType.MANUAL_INPUT) {
            throw new UnsupportedOperationException("Only MANUAL_INPUT knowledge can be edited");
        }

        knowledge.setContent(newContent);
        return botKnowledgeRepository.save(knowledge);
    }

    @Transactional(readOnly = true)
    public Optional<String> getKnowledgeContentByUserId(Long userId) {
        return botKnowledgeRepository
                .findByOwnerId(userId)      // Optional<BotKnowledge>
                .map(BotKnowledge::getContent); // Optional<String>
    }
}
