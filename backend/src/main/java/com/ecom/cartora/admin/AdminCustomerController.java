package com.ecom.cartora.admin;

import com.ecom.cartora.user.Role;
import com.ecom.cartora.user.UserRepo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/customers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {
    private final UserRepo userRepo;

    public AdminCustomerController(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping
    public List<AdminCustomerResponse> listCustomers() {
        return userRepo.findByRoleOrderByCreatedAtDesc(Role.CUSTOMER).stream()
                .map(user -> new AdminCustomerResponse(user.getId(), user.getName(),
                        user.getEmail(), user.getRole(), user.getCreatedAt()))
                .toList();
    }
}
