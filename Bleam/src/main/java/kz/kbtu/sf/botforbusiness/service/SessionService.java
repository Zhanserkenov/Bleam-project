package kz.kbtu.sf.botforbusiness.service;

import jakarta.persistence.EntityNotFoundException;
import kz.kbtu.sf.botforbusiness.model.PlatformType;
import kz.kbtu.sf.botforbusiness.model.Session;
import kz.kbtu.sf.botforbusiness.model.User;
import kz.kbtu.sf.botforbusiness.repository.SessionRepository;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public SessionService(SessionRepository sessionRepository, UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    public Session getOrCreateSession(Long userId, String chatUserId, PlatformType platformType) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));

        Optional<Session> existingSession = sessionRepository.findByChatUserIdAndPlatformType(chatUserId, platformType);
        if (existingSession.isPresent()) {
            return existingSession.get();
        }

        Session session = new Session(chatUserId, platformType);
        session.setOwner(user);

        return sessionRepository.save(session);
    }
}
