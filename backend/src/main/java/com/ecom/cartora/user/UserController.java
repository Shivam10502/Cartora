package com.ecom.cartora.user;

import com.ecom.cartora.user.dto.UserRegisterRequest;
import com.ecom.cartora.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService service;

    @PostMapping("/register")
    public UserResponse register(
            @Valid @RequestBody UserRegisterRequest request) {

        return service.registerNewUser(request);
    }
}
