package kz.kbtu.sf.botforbusiness.service;

import kz.kbtu.sf.botforbusiness.dto.PlatformDTO;
import kz.kbtu.sf.botforbusiness.dto.PlatformRequest;
import kz.kbtu.sf.botforbusiness.model.PlatformStatus;
import kz.kbtu.sf.botforbusiness.model.Session;

import java.util.List;
import java.util.Optional;

public interface PlatformService {
    void startPlatform(Long userId, PlatformRequest platformRequest);
    void stopPlatform(Long userId);
    default List<Long> listAllIds() {
        throw new UnsupportedOperationException("Not implemented");
    }
    default List<PlatformRequest> listAllInfos() {
        throw new UnsupportedOperationException("Not implemented");
    }
    PlatformDTO getPlatformInfo(Long userId);
    List<Session> getAllSessions(Long userId);
    Optional<PlatformStatus> getPlatformStatus(Long userId);
}
