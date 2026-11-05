package com.ecom.cartora.wishlist;

import com.ecom.cartora.wishlist.dto.WishlistItemRequest;
import com.ecom.cartora.wishlist.dto.WishlistItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public List<WishlistItemResponse> getWishlist() {
        return wishlistService.getMyWishlist();
    }

    @PostMapping("/items")
    public WishlistItemResponse addProduct(
            @Valid @RequestBody WishlistItemRequest request) {
        return wishlistService.addProduct(request);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeProduct(@PathVariable("productId") Long productId) {
        wishlistService.removeProduct(productId);
        return ResponseEntity.noContent().build();
    }
}
