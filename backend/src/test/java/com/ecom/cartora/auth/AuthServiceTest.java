package com.ecom.cartora.auth;

import com.ecom.cartora.security.JwtService;
import com.ecom.cartora.user.Role;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import com.ecom.cartora.user.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepo userRepo;
    @Mock private JwtService jwtService;
    @InjectMocks private AuthService authService;

    @Test
    void successfulLoginNormalizesEmailAndIssuesRoleScopedJwt() {
        User customer = new User();
        customer.setEmail("customer@example.com");
        customer.setPassword("encoded-password");
        customer.setRole(Role.CUSTOMER);
        when(userRepo.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(customer));
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("signed-jwt");

        var response = authService.login(new LoginRequest("  CUSTOMER@Example.com ", "correct-password"));

        assertThat(response.getToken()).isEqualTo("signed-jwt");
        ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        assertThat(authenticationCaptor.getValue().getName()).isEqualTo("customer@example.com");
        assertThat(authenticationCaptor.getValue().getCredentials()).isEqualTo("correct-password");
        ArgumentCaptor<UserDetails> userDetailsCaptor = ArgumentCaptor.forClass(UserDetails.class);
        verify(jwtService).generateToken(userDetailsCaptor.capture());
        assertThat(userDetailsCaptor.getValue().getUsername()).isEqualTo("customer@example.com");
        assertThat(userDetailsCaptor.getValue().getAuthorities())
                .extracting("authority").containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void rejectedCredentialsStopBeforeReadingUserOrCreatingToken() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("customer@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepo, never()).findByEmailIgnoreCase(any());
        verify(jwtService, never()).generateToken(any(UserDetails.class));
    }
}
