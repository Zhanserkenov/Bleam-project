package kz.kbtu.sf.botforbusiness.repository;

import kz.kbtu.sf.botforbusiness.model.PlatformStatus;
import kz.kbtu.sf.botforbusiness.model.TelegramPlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramRepository extends JpaRepository<TelegramPlatform, Long> {
    Optional<TelegramPlatform> findByOwnerId(Long userId);
    List<TelegramPlatform> findAllByStatus(PlatformStatus status);
}
