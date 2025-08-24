package kz.kbtu.sf.botforbusiness.repository;

import kz.kbtu.sf.botforbusiness.model.BotKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BotKnowledgeRepository extends JpaRepository<BotKnowledge, Long> {
    Optional<BotKnowledge> findByOwnerId(Long userId);
}
