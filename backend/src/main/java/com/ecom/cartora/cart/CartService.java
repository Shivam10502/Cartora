package com.ecom.cartora.cart;

import com.ecom.cartora.cart.dto.AddToCartRequest;
import com.ecom.cartora.cart.dto.CartItemResponse;
import com.ecom.cartora.cart.dto.CartResponse;
import com.ecom.cartora.cart.dto.UpdateCartItemRequest;
import com.ecom.cartora.exception.*;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductRepo;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private CartItemRepo cartItemRepo;
    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ProductRepo productRepo;

    @Transactional
    public CartResponse addProductTocart(AddToCartRequest request){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();
        User user = userRepo.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Cart cart = cartRepo.findByUserId(user.getId())
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(user);

                    LocalDateTime now = LocalDateTime.now();
                    newCart.setCreatedAt(now);
                    newCart.setUpdatedAt(now);

                    return cartRepo.save(newCart);
                });
        Product product = productRepo.findById(request.getProductId())
                .orElseThrow(() ->
                        new ProductNotFoundException("Product not found"));

        int requestedQuantity = request.getQuantity();

        if (requestedQuantity > product.getStockQuantity()) {
            throw new InsufficientStockException("Insufficient stock");
        }
        Optional<CartItem> existingItem =
                cartItemRepo.findByCartIdAndProductId(
                        cart.getId(),
                        product.getId()
                );
        if (existingItem.isPresent()) {

            CartItem cartItem = existingItem.get();

            int newQuantity =
                    cartItem.getQuantity() + requestedQuantity;

            if (newQuantity > product.getStockQuantity()) {
                throw new InsufficientStockException("Insufficient stock");
            }
            cartItem.setQuantity(newQuantity);
            cartItemRepo.save(cartItem);
        }

        else{
            CartItem newCartItem = new CartItem();
            newCartItem.setCart(cart);
            newCartItem.setProduct(product);
            newCartItem.setQuantity(requestedQuantity);
            cartItemRepo.save(newCartItem);
        }
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepo.save(cart);

        List<CartItem> cartItems = cartItemRepo.findByCartId(cart.getId());

        List<CartItemResponse> itemResponses = new ArrayList<>();
        for(CartItem item : cartItems){
            Product itemProduct = item.getProduct();
            CartItemResponse response = new CartItemResponse();
            response.setCartItemId(item.getId());
            response.setProductId(itemProduct.getId());
            response.setProductName(itemProduct.getName());
            response.setPrice(itemProduct.getPrice());
            response.setQuantity(item.getQuantity());
            BigDecimal subtotal =
                    itemProduct.getPrice()
                            .multiply(
                                    BigDecimal.valueOf(item.getQuantity())
                            );

            response.setSubtotal(subtotal);

            itemResponses.add(response);

        }
        CartResponse cartResponse = new CartResponse();

        cartResponse.setCartId(cart.getId());
        cartResponse.setUserId(user.getId());
        cartResponse.setItems(itemResponses);

        return cartResponse;
    }

    public CartResponse getCart(){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();
        User user = userRepo.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Cart cart = cartRepo.findByUserId(user.getId())
                .orElseThrow(() -> new CartNotFoundException("Cart Not Found"));


        List<CartItem> cartItems = cartItemRepo.findByCartId(cart.getId());

        List<CartItemResponse> itemResponses = new ArrayList<>();
        for(CartItem item : cartItems){
            Product itemProduct = item.getProduct();
            CartItemResponse response = new CartItemResponse();
            response.setCartItemId(item.getId());
            response.setProductId(itemProduct.getId());
            response.setProductName(itemProduct.getName());
            response.setPrice(itemProduct.getPrice());
            response.setQuantity(item.getQuantity());
            BigDecimal subtotal =
                    itemProduct.getPrice()
                            .multiply(
                                    BigDecimal.valueOf(item.getQuantity())
                            );

            response.setSubtotal(subtotal);

            itemResponses.add(response);

        }
        CartResponse cartResponse = new CartResponse();

        cartResponse.setCartId(cart.getId());
        cartResponse.setUserId(user.getId());
        cartResponse.setItems(itemResponses);

        return cartResponse;
    }

    public CartResponse updateCartItem(Long cartItemId, UpdateCartItemRequest request){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();
        User user = userRepo.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Cart cart = cartRepo.findByUserId(user.getId())
                .orElseThrow(() -> new CartNotFoundException("Cart Not Found"));

        List<CartItem> cartItems = cartItemRepo.findByCartId(cart.getId());

    }

}
