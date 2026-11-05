package com.ecom.cartora.cart;

import com.ecom.cartora.cart.dto.AddToCartRequest;
import com.ecom.cartora.cart.dto.CartResponse;
import com.ecom.cartora.cart.dto.UpdateCartItemRequest;
import com.ecom.cartora.cart.entity.Cart;
import com.ecom.cartora.cart.entity.CartItem;
import com.ecom.cartora.cart.repository.CartItemRepo;
import com.ecom.cartora.cart.repository.CartRepo;
import com.ecom.cartora.exception.CartItemNotFoundException;
import com.ecom.cartora.exception.InsufficientStockException;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductRepo;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartRepo cartRepo;
    @Mock private CartItemRepo cartItemRepo;
    @Mock private UserRepo userRepo;
    @Mock private ProductRepo productRepo;
    @InjectMocks private CartService cartService;

    private User user;
    private Cart cart;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setEmail("customer@cartora.test");
        cart = new Cart();
        cart.setId(12L);
        cart.setUser(user);
        product = new Product();
        product.setId(23L);
        product.setName("Desk lamp");
        product.setPrice(new BigDecimal("19.95"));
        product.setStockQuantity(10);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "", List.of()));
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addProductToCart_createsCartAndItemWhenCustomerHasNoCart() {
        // Arrange: no cart exists yet; saving it returns the new cart fixture.
        when(cartRepo.findByUserIdForUpdate(user.getId())).thenReturn(Optional.empty());
        when(cartRepo.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });
        when(productRepo.findById(product.getId())).thenReturn(Optional.of(product));
        when(cartItemRepo.findByCartIdAndProductId(12L, product.getId())).thenReturn(Optional.empty());
        when(cartItemRepo.findByCartId(12L)).thenAnswer(invocation -> {
            CartItem item = new CartItem();
            item.setId(31L);
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(2);
            return List.of(item);
        });

        // Act: add two units of the product.
        CartResponse response = cartService.addProductToCart(new AddToCartRequest(product.getId(), 2));

        // Assert: the new cart and item are persisted and the response contains the correct subtotal.
        assertThat(response.getCartId()).isEqualTo(12L);
        assertThat(response.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductId()).isEqualTo(product.getId());
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getSubtotal()).isEqualByComparingTo("39.90");
        });
        verify(cartItemRepo).save(any(CartItem.class));
    }

    @Test
    void addProductToCart_mergesQuantityWithExistingItem() {
        // Arrange: a cart already has three units of this product.
        CartItem existing = new CartItem();
        existing.setId(31L);
        existing.setCart(cart);
        existing.setProduct(product);
        existing.setQuantity(3);
        when(cartRepo.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(productRepo.findById(product.getId())).thenReturn(Optional.of(product));
        when(cartItemRepo.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.of(existing));
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of(existing));

        // Act: add two more units.
        CartResponse response = cartService.addProductToCart(new AddToCartRequest(product.getId(), 2));

        // Assert: the existing row is updated to five units rather than duplicated.
        assertThat(existing.getQuantity()).isEqualTo(5);
        assertThat(response.getItems()).singleElement().extracting("quantity").isEqualTo(5);
        verify(cartItemRepo).save(existing);
    }

    @Test
    void addProductToCart_rejectsQuantityAboveAvailableStock() {
        // Arrange: the request exceeds product stock.
        when(cartRepo.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(productRepo.findById(product.getId())).thenReturn(Optional.of(product));

        // Act and assert: reject before looking up or saving a cart item.
        assertThatThrownBy(() -> cartService.addProductToCart(new AddToCartRequest(product.getId(), 11)))
                .isInstanceOf(InsufficientStockException.class);
        verify(cartItemRepo, never()).save(any(CartItem.class));
    }

    @Test
    void updateCartItem_rejectsItemOwnedByAnotherCart() {
        // Arrange: the located row belongs to a different customer's cart.
        Cart otherCart = new Cart();
        otherCart.setId(99L);
        CartItem foreignItem = new CartItem();
        foreignItem.setId(31L);
        foreignItem.setCart(otherCart);
        foreignItem.setProduct(product);
        when(cartRepo.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepo.findById(31L)).thenReturn(Optional.of(foreignItem));

        // Act and assert: treat the foreign row as not found and leave persistence untouched.
        assertThatThrownBy(() -> cartService.updateCartItem(31L, new UpdateCartItemRequest(1)))
                .isInstanceOf(CartItemNotFoundException.class)
                .hasMessageContaining("does not belong");
        verify(cartItemRepo, never()).save(any(CartItem.class));
    }

    @Test
    void updateCartItem_rejectsQuantityAboveAvailableStock() {
        // Arrange: the owned item is present, but requested quantity exceeds inventory.
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        when(cartRepo.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepo.findById(31L)).thenReturn(Optional.of(item));

        // Act and assert: stock validation happens before the item is saved.
        assertThatThrownBy(() -> cartService.updateCartItem(31L, new UpdateCartItemRequest(11)))
                .isInstanceOf(InsufficientStockException.class);
        verify(cartItemRepo, never()).save(any(CartItem.class));
    }

    @Test
    void clearCart_deletesAllRowsForTheCurrentCustomer() {
        // Arrange: the authenticated customer owns cart 12.
        when(cartRepo.findByUserId(user.getId())).thenReturn(Optional.of(cart));

        // Act: clear the customer's cart.
        cartService.clearCart();

        // Assert: only rows for that cart are deleted and its updated timestamp is persisted.
        verify(cartItemRepo).deleteByCartId(cart.getId());
        verify(cartRepo).save(cart);
        assertThat(cart.getUpdatedAt()).isNotNull();
    }
}
