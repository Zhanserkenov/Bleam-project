package kz.kbtu.sf.botforbusiness.repository;

import kz.kbtu.sf.botforbusiness.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findAllBySessionIdOrderByTimestampAsc(Long sessionId);
}
