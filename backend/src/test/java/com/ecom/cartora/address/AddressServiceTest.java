package com.ecom.cartora.address;

import com.ecom.cartora.address.dto.AddressRequest;
import com.ecom.cartora.address.dto.AddressResponse;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepo addressRepo;
    @Mock
    private UserRepo userRepo;

    @InjectMocks
    private AddressService addressService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(3L);
        user.setEmail("customer@cartora.test");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        "password",
                        List.of()));
        when(userRepo.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAddress_shouldMakeFirstAddressTheDefault() {
        // Arrange
        when(addressRepo.findByUserIdOrderByCreatedAtAsc(user.getId()))
                .thenReturn(List.of());
        when(addressRepo.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AddressRequest request = new AddressRequest(
                "Asha Rao", "12 Park Street", "Near the library",
                "Pune", "Maharashtra", "411001", "India");

        // Act
        AddressResponse response = addressService.createAddress(request);

        // Assert
        assertThat(response.isDefaultAddress()).isTrue();
        assertThat(response.getRecipientName()).isEqualTo("Asha Rao");
        verify(addressRepo).save(any(Address.class));
    }

    @Test
    void deleteAddress_shouldPromoteRemainingAddress_whenDeletingDefault() {
        // Arrange
        Address currentDefault = new Address();
        currentDefault.setId(10L);
        currentDefault.setUser(user);
        currentDefault.setDefaultAddress(true);

        Address replacement = new Address();
        replacement.setId(11L);
        replacement.setUser(user);

        when(addressRepo.findByIdAndUserId(10L, user.getId()))
                .thenReturn(Optional.of(currentDefault));
        when(addressRepo.findByUserIdOrderByCreatedAtAsc(user.getId()))
                .thenReturn(List.of(replacement));
        when(addressRepo.save(replacement)).thenReturn(replacement);

        // Act
        addressService.deleteAddress(10L);

        // Assert
        assertThat(replacement.isDefaultAddress()).isTrue();
        verify(addressRepo).delete(currentDefault);
        verify(addressRepo).save(replacement);
        verify(addressRepo).flush();
    }

    @Test
    void setDefaultAddress_shouldClearCurrentDefaultAndSetRequestedAddress() {
        Address current = new Address();
        current.setId(10L);
        current.setUser(user);
        current.setDefaultAddress(true);
        Address selected = new Address();
        selected.setId(11L);
        selected.setUser(user);
        when(addressRepo.findByIdAndUserId(11L, user.getId())).thenReturn(Optional.of(selected));
        when(addressRepo.findByUserIdOrderByCreatedAtAsc(user.getId())).thenReturn(List.of(current, selected));
        when(addressRepo.saveAndFlush(selected)).thenReturn(selected);

        AddressResponse response = addressService.setDefaultAddress(11L);

        assertThat(current.isDefaultAddress()).isFalse();
        assertThat(selected.isDefaultAddress()).isTrue();
        assertThat(response.isDefaultAddress()).isTrue();
        verify(addressRepo).saveAllAndFlush(List.of(current, selected));
        verify(addressRepo).saveAndFlush(selected);
    }
}
