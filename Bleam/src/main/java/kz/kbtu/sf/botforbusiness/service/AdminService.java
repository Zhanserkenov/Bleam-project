package kz.kbtu.sf.botforbusiness.service;

import kz.kbtu.sf.botforbusiness.model.Role;
import kz.kbtu.sf.botforbusiness.model.User;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void approveUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        user.setRole(Role.USER);
        userRepository.save(user);
    }
}
