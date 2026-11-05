package com.ecom.cartora.review;

import com.ecom.cartora.review.dto.ProductRatingSummary;
import com.ecom.cartora.review.dto.ProductReviewRequest;
import com.ecom.cartora.review.dto.ProductReviewResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ProductReviewController {

    private final ProductReviewService reviewService;

    public ProductReviewController(ProductReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ProductReviewResponse> getReviews(@PathVariable("productId") Long productId) {
        return reviewService.getProductReviews(productId);
    }

    @GetMapping("/summary")
    public ProductRatingSummary getRatingSummary(@PathVariable("productId") Long productId) {
        return reviewService.getRatingSummary(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductReviewResponse createReview(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody ProductReviewRequest request) {
        return reviewService.createReview(productId, request);
    }

    @PutMapping("/me")
    public ProductReviewResponse updateMyReview(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody ProductReviewRequest request) {
        return reviewService.updateMyReview(productId, request);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyReview(@PathVariable("productId") Long productId) {
        reviewService.deleteMyReview(productId);
        return ResponseEntity.noContent().build();
    }
}
