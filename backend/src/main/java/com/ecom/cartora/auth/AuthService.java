package com.ecom.cartora.auth;

import com.ecom.cartora.auth.dto.LoginResponse;
import com.ecom.cartora.security.JwtService;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import com.ecom.cartora.user.dto.LoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JwtService jwtService;

    public LoginResponse login(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        request.getPassword()
                )
        );

        User user = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));

        UserDetails userDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPassword())
                        .roles(user.getRole().name())
                        .build();

        String token =
                jwtService.generateToken(userDetails);

        return new LoginResponse(token);
    }
}
