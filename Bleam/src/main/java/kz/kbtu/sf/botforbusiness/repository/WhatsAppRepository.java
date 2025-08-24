package kz.kbtu.sf.botforbusiness.repository;

import kz.kbtu.sf.botforbusiness.model.PlatformStatus;
import kz.kbtu.sf.botforbusiness.model.WhatsAppPlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppRepository extends JpaRepository<WhatsAppPlatform, Long> {
    Optional<WhatsAppPlatform> findByOwnerId(Long userId);
    List<WhatsAppPlatform> findAllByStatus(PlatformStatus status);
}
