package com.ecom.cartora.cart;

import com.ecom.cartora.cart.dto.AddToCartRequest;
import com.ecom.cartora.cart.dto.CartResponse;
import com.ecom.cartora.cart.dto.UpdateCartItemRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @PostMapping("/items")
    public CartResponse addToCart(@Valid @RequestBody AddToCartRequest request){
        return cartService.addProductToCart(request);
    }

    @GetMapping
    public CartResponse getCart(){
        return cartService.getCart();
    }

    @PutMapping("/items/{cartItemId}")
    public CartResponse updateCart(@PathVariable Long cartItemId, @Valid @RequestBody UpdateCartItemRequest request){
        return cartService.updateCartItem(cartItemId,request);
    }

    @DeleteMapping("/items/{cartItemId}")
    public CartResponse removeOneCartItem(@PathVariable Long cartItemId){
        return cartService.removeCartItem(cartItemId);
    }

    @DeleteMapping
    public ResponseEntity<String> clearAll(){
        cartService.clearCart();
        return ResponseEntity.ok("Cart cleared successfully");
    }

}
