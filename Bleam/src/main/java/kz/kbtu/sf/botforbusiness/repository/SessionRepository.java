package kz.kbtu.sf.botforbusiness.repository;

import kz.kbtu.sf.botforbusiness.model.PlatformType;
import kz.kbtu.sf.botforbusiness.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByChatUserIdAndPlatformType(String chatUserId, PlatformType platformType);
    List<Session> findByOwnerIdAndPlatformType(Long userId, PlatformType platform);
}
