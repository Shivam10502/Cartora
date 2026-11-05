package com.ecom.cartora.user;

import com.ecom.cartora.exception.UserAlreadyExistsException;
import com.ecom.cartora.user.dto.UserRegisterRequest;
import com.ecom.cartora.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepo userRepo;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerNewUser_shouldEncodePasswordAndAssignCustomerRole() {
        // Arrange
        UserRegisterRequest request = new UserRegisterRequest(
                "Asha Rao", "asha@example.test", "StrongPass123");
        when(userRepo.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        // Act
        UserResponse response = userService.registerNewUser(request);

        // Assert
        assertThat(response.getId()).isEqualTo(12L);
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-password");
        verify(passwordEncoder).encode(request.getPassword());
    }

    @Test
    void registerNewUser_shouldRejectExistingEmailWithoutEncodingPassword() {
        // Arrange
        UserRegisterRequest request = new UserRegisterRequest(
                "Asha Rao", "asha@example.test", "StrongPass123");
        when(userRepo.existsByEmailIgnoreCase(request.getEmail())).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> userService.registerNewUser(request))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(passwordEncoder, never()).encode(any());
        verify(userRepo, never()).save(any(User.class));
    }
}
