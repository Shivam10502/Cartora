package com.ecom.cartora.product;

import com.ecom.cartora.exception.ProductNotFoundException;
import com.ecom.cartora.product.dto.ProductResponse;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecentlyViewedProductService {

    private final RecentlyViewedProductRepo recentlyViewedProductRepo;
    private final UserRepo userRepo;
    private final ProductRepo productRepo;

    public RecentlyViewedProductService(RecentlyViewedProductRepo recentlyViewedProductRepo,
            UserRepo userRepo, ProductRepo productRepo) {
        this.recentlyViewedProductRepo = recentlyViewedProductRepo;
        this.userRepo = userRepo;
        this.productRepo = productRepo;
    }

    @Transactional
    public void recordView(Long productId) {
        User user = getUserForUpdate();
        if (!productRepo.existsById(productId)) {
            throw new ProductNotFoundException("Product not found");
        }
        recentlyViewedProductRepo.upsert(user.getId(), productId, LocalDateTime.now());
        recentlyViewedProductRepo.deleteOlderViews(user.getId());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getRecentProducts() {
        User user = getUser();
        return recentlyViewedProductRepo.findRecentProducts(user.getId()).stream()
                .map(view -> toResponse(view.getProduct()))
                .toList();
    }

    private User getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepo.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private User getUserForUpdate() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepo.findByEmailIgnoreCaseForUpdate(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getImageUrl(), product.getPrice(), product.getStockQuantity(), product.getSku(),
                product.getCategory().getCategoryId(), product.getCreatedAt(), product.getUpdatedAt());
    }
}
