package com.ecom.cartora.checkout;

import com.ecom.cartora.cart.entity.Cart;
import com.ecom.cartora.cart.entity.CartItem;
import com.ecom.cartora.cart.repository.CartItemRepo;
import com.ecom.cartora.cart.repository.CartRepo;
import com.ecom.cartora.checkout.dto.CheckoutRequest;
import com.ecom.cartora.checkout.dto.CheckoutResponse;
import com.ecom.cartora.checkout.dto.ShippingAddressRequest;
import com.ecom.cartora.coupon.CouponDiscount;
import com.ecom.cartora.coupon.CouponService;
import com.ecom.cartora.exception.CartIsEmptyException;
import com.ecom.cartora.exception.InsufficientStockException;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductInventoryService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock private CartRepo cartRepo;
    @Mock private CartItemRepo cartItemRepo;
    @Mock private UserRepo userRepo;
    @Mock private CouponService couponService;
    @Mock private ProductInventoryService productInventoryService;
    @InjectMocks private CheckoutService checkoutService;

    private User user;
    private Cart cart;
    private Product product;
    private ShippingAddressRequest shippingAddress;

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
        shippingAddress = new ShippingAddressRequest(
                "Asha Rao", "12 Park Street", "Near the library",
                "Pune", "Maharashtra", "411001", "India");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "", List.of()));
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(cartRepo.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        lenient().when(productInventoryService.lockAndRefresh(org.mockito.ArgumentMatchers.anyCollection()))
                .thenAnswer(invocation -> java.util.Map.of(product.getId(), product));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createCheckout_calculatesLineTotalsAndAppliesCouponDiscount() {
        // Arrange: two units cost 39.90 before a 5.00 coupon discount.
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(2);
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of(item));
        when(couponService.calculateDiscount("SAVE5", new BigDecimal("39.90")))
                .thenReturn(new CouponDiscount("SAVE5", new BigDecimal("5.00")));

        // Act: request checkout using the valid shipping address and coupon.
        CheckoutResponse response = checkoutService.createCheckout(new CheckoutRequest(shippingAddress, "SAVE5"));

        // Assert: item, subtotal, discount, final total, and address are returned accurately.
        assertThat(response.getShippingAddress()).isEqualTo(shippingAddress);
        assertThat(response.getItems()).singleElement().satisfies(result -> {
            assertThat(result.getProductId()).isEqualTo(product.getId());
            assertThat(result.getProductName()).isEqualTo("Desk lamp");
            assertThat(result.getQuantity()).isEqualTo(2);
            assertThat(result.getSubtotal()).isEqualByComparingTo("39.90");
        });
        assertThat(response.getSubtotal()).isEqualByComparingTo("39.90");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("5.00");
        assertThat(response.getCouponCode()).isEqualTo("SAVE5");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("34.90");
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test
    void createCheckout_returnsFullSubtotalWhenCouponIsNotProvided() {
        // Arrange: one unit is available and no coupon was supplied.
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(1);
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of(item));
        when(couponService.calculateDiscount(null, new BigDecimal("19.95")))
                .thenReturn(CouponDiscount.none());

        // Act: create checkout without a coupon code.
        CheckoutResponse response = checkoutService.createCheckout(new CheckoutRequest(shippingAddress, null));

        // Assert: the discount is zero and the payable amount equals the subtotal.
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("19.95");
        assertThat(response.getCouponCode()).isNull();
    }

    @Test
    void createCheckout_rejectsEmptyCartBeforeCouponCalculation() {
        // Arrange: the current customer's cart has no items.
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of());

        // Act and assert: empty carts cannot be checked out.
        assertThatThrownBy(() -> checkoutService.createCheckout(new CheckoutRequest(shippingAddress, null)))
                .isInstanceOf(CartIsEmptyException.class);
        verify(couponService, never()).calculateDiscount(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createCheckout_rejectsCartQuantityAboveCurrentStock() {
        // Arrange: the cart's requested quantity is greater than current stock.
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(11);
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of(item));

        // Act and assert: checkout stops before coupon evaluation when stock is insufficient.
        assertThatThrownBy(() -> checkoutService.createCheckout(new CheckoutRequest(shippingAddress, null)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Desk lamp");
        verify(couponService, never()).calculateDiscount(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
