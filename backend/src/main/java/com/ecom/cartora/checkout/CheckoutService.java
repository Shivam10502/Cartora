package com.ecom.cartora.checkout;

import com.ecom.cartora.cart.repository.CartItemRepo;
import com.ecom.cartora.cart.repository.CartRepo;
import com.ecom.cartora.cart.entity.Cart;
import com.ecom.cartora.cart.entity.CartItem;
import com.ecom.cartora.checkout.dto.CheckoutItemResponse;
import com.ecom.cartora.checkout.dto.CheckoutRequest;
import com.ecom.cartora.checkout.dto.CheckoutResponse;
import com.ecom.cartora.coupon.CouponDiscount;
import com.ecom.cartora.coupon.CouponService;
import com.ecom.cartora.exception.CartIsEmptyException;
import com.ecom.cartora.exception.CartNotFoundException;
import com.ecom.cartora.exception.InsufficientStockException;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductInventoryService;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {

    private final CartRepo cartRepo;
    private final CartItemRepo cartItemRepo;
    private final UserRepo userRepo;
    private final CouponService couponService;
    private final ProductInventoryService productInventoryService;

    public CheckoutService(CartRepo cartRepo, CartItemRepo cartItemRepo, UserRepo userRepo,
            CouponService couponService, ProductInventoryService productInventoryService) {
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.userRepo = userRepo;
        this.couponService = couponService;
        this.productInventoryService = productInventoryService;
    }

    private User getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        return userRepo.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Transactional
    public CheckoutResponse createCheckout(CheckoutRequest request) {
        User user = getUser();

        Cart cart = cartRepo.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new CartNotFoundException("Cart Not Found"));

        List<CartItem> cartItems = cartItemRepo.findByCartId(cart.getId());

        if (cartItems.isEmpty()) {
            throw new CartIsEmptyException("Cart Is Empty");
        }
        var products = productInventoryService.lockAndRefresh(cartItems.stream()
                .map(item -> item.getProduct().getId()).toList());
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<CheckoutItemResponse> items = new ArrayList<>();
        for (CartItem item : cartItems) {

            Product product = products.get(item.getProduct().getId());

            if (item.getQuantity() > product.getStockQuantity()) {
                throw new InsufficientStockException(
                        "Required quantity is not available for product: "
                                + product.getName());
            }
            BigDecimal price = product.getPrice();
            BigDecimal subtotal = price.multiply(
                    BigDecimal.valueOf(item.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            CheckoutItemResponse checkoutItemResponse = new CheckoutItemResponse();
            checkoutItemResponse.setProductId(product.getId());
            checkoutItemResponse.setProductName(product.getName());
            checkoutItemResponse.setQuantity(item.getQuantity());
            checkoutItemResponse.setPrice(product.getPrice());
            checkoutItemResponse.setSubtotal(subtotal);
            items.add(checkoutItemResponse);
        }

        CouponDiscount couponDiscount = couponService.calculateDiscount(request.getCouponCode(), totalAmount);

        CheckoutResponse checkoutResponse = new CheckoutResponse();
        checkoutResponse.setShippingAddress(request.getShippingAddress());
        checkoutResponse.setItems(items);
        checkoutResponse.setSubtotal(totalAmount);
        checkoutResponse.setDiscountAmount(couponDiscount.amount());
        checkoutResponse.setCouponCode(couponDiscount.code());
        checkoutResponse.setTotalAmount(totalAmount.subtract(couponDiscount.amount()));
        checkoutResponse.setCreatedAt(LocalDateTime.now());

        return checkoutResponse;
    }
}
