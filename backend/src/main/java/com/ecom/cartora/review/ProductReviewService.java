package com.ecom.cartora.review;

import com.ecom.cartora.exception.ProductNotFoundException;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductRepo;
import com.ecom.cartora.review.dto.ProductRatingSummary;
import com.ecom.cartora.review.dto.ProductReviewRequest;
import com.ecom.cartora.review.dto.ProductReviewResponse;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
public class ProductReviewService {

    private static final EnumSet<OrderStatus> VERIFIED_ORDER_STATUSES = EnumSet.of(OrderStatus.CONFIRMED,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final ProductReviewRepo reviewRepo;
    private final ProductRepo productRepo;
    private final OrderItemRepo orderItemRepo;
    private final UserRepo userRepo;

    public ProductReviewService(
            ProductReviewRepo reviewRepo,
            ProductRepo productRepo,
            OrderItemRepo orderItemRepo,
            UserRepo userRepo) {
        this.reviewRepo = reviewRepo;
        this.productRepo = productRepo;
        this.orderItemRepo = orderItemRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public List<ProductReviewResponse> getProductReviews(Long productId) {
        requireProduct(productId);
        return reviewRepo.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductRatingSummary getRatingSummary(Long productId) {
        List<ProductReview> reviews = reviewRepo.findByProductIdOrderByCreatedAtDesc(productId);
        if (reviews.isEmpty()) {
            requireProduct(productId);
            return new ProductRatingSummary(BigDecimal.ZERO.setScale(2), 0);
        }
        BigDecimal average = reviews.stream()
                .mapToInt(ProductReview::getRating)
                .average()
                .stream()
                .mapToObj(BigDecimal::valueOf)
                .findFirst()
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return new ProductRatingSummary(average, reviews.size());
    }

    @Transactional
    public ProductReviewResponse createReview(
            Long productId,
            ProductReviewRequest request) {
        User user = getAuthenticatedUser();
        Product product = requireProduct(productId);

        if (reviewRepo.findByProductIdAndUserId(productId, user.getId()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You have already reviewed this product");
        }

        boolean verifiedPurchase = !orderItemRepo
                .findByProduct_IdAndOrder_User_IdAndOrder_StatusIn(
                        productId,
                        user.getId(),
                        VERIFIED_ORDER_STATUSES)
                .isEmpty();
        if (!verifiedPurchase) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "A completed purchase is required to review this product");
        }

        LocalDateTime now = LocalDateTime.now();
        ProductReview review = new ProductReview();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(request.getRating());
        review.setTitle(request.getTitle().trim());
        review.setComment(request.getComment().trim());
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        return toResponse(reviewRepo.save(review));
    }

    @Transactional
    public ProductReviewResponse updateMyReview(
            Long productId,
            ProductReviewRequest request) {
        User user = getAuthenticatedUser();
        ProductReview review = findOwnedReview(productId, user.getId());
        review.setRating(request.getRating());
        review.setTitle(request.getTitle().trim());
        review.setComment(request.getComment().trim());
        review.setUpdatedAt(LocalDateTime.now());
        return toResponse(reviewRepo.save(review));
    }

    @Transactional
    public void deleteMyReview(Long productId) {
        User user = getAuthenticatedUser();
        reviewRepo.delete(findOwnedReview(productId, user.getId()));
    }

    private ProductReview findOwnedReview(Long productId, Long userId) {
        return reviewRepo.findByProductIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Review not found"));
    }

    private Product requireProduct(Long productId) {
        return productRepo.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required");
        }
        return userRepo.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private ProductReviewResponse toResponse(ProductReview review) {
        return new ProductReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                review.getUser().getId(),
                review.getUser().getName(),
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}