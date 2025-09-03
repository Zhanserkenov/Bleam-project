package kz.kbtu.sf.botforbusiness.controller;

import kz.kbtu.sf.botforbusiness.dto.UserInfo;
import kz.kbtu.sf.botforbusiness.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public List<UserInfo> getAllUsers() {
        return adminService.getAllUsers();
    }

    @PostMapping("/changeRole/{userId}")
    public ResponseEntity<String> approveUser(@PathVariable Long userId) {
        String newRole = adminService.changeRole(userId);
        return ResponseEntity.ok("Role changed to " + newRole);
    }
}
