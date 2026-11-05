package com.ecom.cartora.wishlist;

import com.ecom.cartora.exception.ProductNotFoundException;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductRepo;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import com.ecom.cartora.wishlist.dto.WishlistItemRequest;
import com.ecom.cartora.wishlist.dto.WishlistItemResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WishlistService {

    private final WishlistRepo wishlistRepo;
    private final ProductRepo productRepo;
    private final UserRepo userRepo;

    public WishlistService(
            WishlistRepo wishlistRepo,
            ProductRepo productRepo,
            UserRepo userRepo) {
        this.wishlistRepo = wishlistRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getMyWishlist() {
        User user = getAuthenticatedUser();
        return wishlistRepo.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WishlistItemResponse addProduct(WishlistItemRequest request) {
        User user = getAuthenticatedUser();
        Product product = productRepo.findById(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));

        return wishlistRepo.findByUserIdAndProductId(user.getId(), product.getId())
                .map(this::toResponse)
                .orElseGet(() -> {
                    WishlistItem item = new WishlistItem();
                    item.setUser(user);
                    item.setProduct(product);
                    item.setCreatedAt(LocalDateTime.now());
                    return toResponse(wishlistRepo.save(item));
                });
    }

    @Transactional
    public void removeProduct(Long productId) {
        User user = getAuthenticatedUser();
        WishlistItem item = wishlistRepo
                .findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product is not in this wishlist"));
        wishlistRepo.delete(item);
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authentication is required");
        }
        return userRepo.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private WishlistItemResponse toResponse(WishlistItem item) {
        Product product = item.getProduct();
        return new WishlistItemResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                item.getCreatedAt());
    }
}