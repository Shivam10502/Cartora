package com.ecom.cartora.checkout;

import com.ecom.cartora.cart.repository.CartItemRepo;
import com.ecom.cartora.cart.repository.CartRepo;
import com.ecom.cartora.cart.entity.Cart;
import com.ecom.cartora.cart.entity.CartItem;
import com.ecom.cartora.checkout.dto.CheckoutItemResponse;
import com.ecom.cartora.checkout.dto.CheckoutRequest;
import com.ecom.cartora.checkout.dto.CheckoutResponse;
import com.ecom.cartora.exception.CartIsEmptyException;
import com.ecom.cartora.exception.CartNotFoundException;
import com.ecom.cartora.exception.InsufficientStockException;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CheckoutService {

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private CartItemRepo cartItemRepo;

    @Autowired
    private UserRepo userRepo;

    private User getUser(){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        return userRepo.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private Cart getUserCart(User user){

        return cartRepo.findByUserId(user.getId())
                .orElseThrow(() -> new CartNotFoundException("Cart Not Found"));
    }

    public CheckoutResponse createCheckout(CheckoutRequest request){
        User user = getUser();

        Cart cart = getUserCart(user);

        List<CartItem> cartItems =
                cartItemRepo.findByCartId(cart.getId());

        if (cartItems.isEmpty()) {
            throw new CartIsEmptyException("Cart Is Empty");
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<CheckoutItemResponse> items = new ArrayList<>();
        for (CartItem item : cartItems) {

            Product product = item.getProduct();

            if (item.getQuantity() > product.getStockQuantity()) {
                throw new InsufficientStockException(
                        "Required quantity is not available for product: "
                                + product.getName()
                );
            }
            BigDecimal price = product.getPrice();
            BigDecimal subtotal =
                    price.multiply(
                            BigDecimal.valueOf(item.getQuantity())
                    );
            totalAmount = totalAmount.add(subtotal);

            CheckoutItemResponse checkoutItemResponse = new CheckoutItemResponse();
            checkoutItemResponse.setProductId(product.getId());
            checkoutItemResponse.setProductName(product.getName());
            checkoutItemResponse.setQuantity(item.getQuantity());
            checkoutItemResponse.setPrice(product.getPrice());
            checkoutItemResponse.setSubtotal(subtotal);
            items.add(checkoutItemResponse);
        }

        CheckoutResponse checkoutResponse = new CheckoutResponse();
        checkoutResponse.setShippingAddress(request.getShippingAddress());
        checkoutResponse.setItems(items);
        checkoutResponse.setTotalAmount(totalAmount);
        checkoutResponse.setCreatedAt(LocalDateTime.now());

        return checkoutResponse;
    }
}
