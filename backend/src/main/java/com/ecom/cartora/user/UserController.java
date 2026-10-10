package com.ecom.cartora.user;

import com.ecom.cartora.user.dto.ChangePasswordRequest;
import com.ecom.cartora.user.dto.UpdateProfileRequest;
import com.ecom.cartora.user.dto.UserRegisterRequest;
import com.ecom.cartora.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService service;

    // POST /api/users/register
    @PostMapping("/register")
    public UserResponse register(
            @Valid @RequestBody UserRegisterRequest request) {

        return service.registerNewUser(request);
    }

    // GET /api/users/me
    @GetMapping("/me")
    public UserResponse getMyProfile() {

        return service.getMyProfile();
    }

    // PUT /api/users/me
    @PutMapping("/me")
    public UserResponse updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request) {

        return service.updateMyProfile(request);
    }

    // PUT /api/users/me/password
    @PutMapping("/me/password")
    public ResponseEntity<String> changeMyPassword(
            @Valid @RequestBody ChangePasswordRequest request) {

        service.changeMyPassword(request);

        return ResponseEntity.ok("Password changed successfully");
    }
}