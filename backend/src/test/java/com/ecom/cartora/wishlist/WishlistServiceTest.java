package com.ecom.cartora.wishlist;

import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductRepo;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import com.ecom.cartora.wishlist.dto.WishlistItemRequest;
import com.ecom.cartora.wishlist.dto.WishlistItemResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private WishlistRepo wishlistRepo;
    @Mock
    private ProductRepo productRepo;
    @Mock
    private UserRepo userRepo;

    @InjectMocks
    private WishlistService wishlistService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(4L);
        user.setEmail("customer@cartora.test");
        product = new Product();
        product.setId(2L);
        product.setName("Desk Lamp");
        product.setPrice(new BigDecimal("1499.00"));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(), "password", java.util.List.of()));
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepo.findById(product.getId())).thenReturn(Optional.of(product));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addProduct_shouldReturnExistingItemWithoutCreatingDuplicate() {
        // Arrange
        WishlistItem existing = new WishlistItem();
        existing.setUser(user);
        existing.setProduct(product);
        when(wishlistRepo.findByUserIdAndProductId(user.getId(), product.getId()))
                .thenReturn(Optional.of(existing));

        // Act
        WishlistItemResponse response = wishlistService.addProduct(
                new WishlistItemRequest(product.getId()));

        // Assert
        assertThat(response.getProductId()).isEqualTo(product.getId());
        assertThat(response.getProductName()).isEqualTo("Desk Lamp");
        verify(wishlistRepo, never()).save(any(WishlistItem.class));
    }

    @Test
    void addProduct_shouldPersistItemForAuthenticatedCustomer() {
        // Arrange
        when(wishlistRepo.findByUserIdAndProductId(user.getId(), product.getId()))
                .thenReturn(Optional.empty());
        when(wishlistRepo.save(any(WishlistItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        WishlistItemResponse response = wishlistService.addProduct(
                new WishlistItemRequest(product.getId()));

        // Assert
        assertThat(response.getProductId()).isEqualTo(product.getId());
        verify(wishlistRepo).save(any(WishlistItem.class));
    }
}