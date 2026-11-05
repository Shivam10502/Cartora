package com.ecom.cartora.review;

import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductRepo;
import com.ecom.cartora.review.dto.ProductRatingSummary;
import com.ecom.cartora.review.dto.ProductReviewRequest;
import com.ecom.cartora.review.dto.ProductReviewResponse;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceTest {

    @Mock
    private ProductReviewRepo reviewRepo;
    @Mock
    private ProductRepo productRepo;
    @Mock
    private OrderItemRepo orderItemRepo;
    @Mock
    private UserRepo userRepo;

    @InjectMocks
    private ProductReviewService reviewService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(5L);
        user.setEmail("buyer@cartora.test");
        user.setName("Asha Rao");
        product = new Product();
        product.setId(2L);
        product.setName("Desk Lamp");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(), "password", List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReview_shouldSaveReviewForVerifiedBuyer() {
        // Arrange
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepo.findById(product.getId())).thenReturn(Optional.of(product));
        when(reviewRepo.findByProductIdAndUserId(product.getId(), user.getId()))
                .thenReturn(Optional.empty());
        when(orderItemRepo.findByProduct_IdAndOrder_User_IdAndOrder_StatusIn(
                eq(product.getId()), eq(user.getId()), anyCollection()))
                .thenReturn(List.of(new OrderItem()));
        when(reviewRepo.save(any(ProductReview.class))).thenAnswer(invocation -> {
            ProductReview saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        ProductReviewRequest request = new ProductReviewRequest(
                5, "Wonderful", "Works beautifully on my desk.");

        // Act
        ProductReviewResponse response = reviewService.createReview(product.getId(), request);

        // Assert
        assertThat(response.getId()).isEqualTo(9L);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getReviewerName()).isEqualTo("Asha Rao");
        verify(reviewRepo).save(any(ProductReview.class));
    }

    @Test
    void createReview_shouldRejectCustomerWithoutCompletedPurchase() {
        // Arrange
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepo.findById(product.getId())).thenReturn(Optional.of(product));
        when(reviewRepo.findByProductIdAndUserId(product.getId(), user.getId()))
                .thenReturn(Optional.empty());
        when(orderItemRepo.findByProduct_IdAndOrder_User_IdAndOrder_StatusIn(
                eq(product.getId()), eq(user.getId()), anyCollection()))
                .thenReturn(List.of());

        // Act + Assert
        assertThatThrownBy(() -> reviewService.createReview(
                product.getId(), new ProductReviewRequest(4, "Good", "A solid product.")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(reviewRepo, never()).save(any(ProductReview.class));
    }

    @Test
    void getRatingSummary_shouldReturnAverageAndCount() {
        // Arrange
        ProductReview fourStar = new ProductReview();
        fourStar.setRating(4);
        ProductReview fiveStar = new ProductReview();
        fiveStar.setRating(5);
        when(reviewRepo.findByProductIdOrderByCreatedAtDesc(product.getId()))
                .thenReturn(List.of(fourStar, fiveStar));

        // Act
        ProductRatingSummary summary = reviewService.getRatingSummary(product.getId());

        // Assert
        assertThat(summary.getAverageRating()).hasToString("4.50");
        assertThat(summary.getReviewCount()).isEqualTo(2);
    }
}