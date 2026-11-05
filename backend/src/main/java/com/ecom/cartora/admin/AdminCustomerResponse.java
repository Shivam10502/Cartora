package com.ecom.cartora.admin;

import com.ecom.cartora.user.Role;

import java.time.LocalDateTime;

public record AdminCustomerResponse(Long id, String name, String email, Role role, LocalDateTime createdAt) {
}
