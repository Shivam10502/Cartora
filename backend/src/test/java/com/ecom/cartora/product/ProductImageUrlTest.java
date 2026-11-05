package com.ecom.cartora.product;

import com.ecom.cartora.category.Category;
import com.ecom.cartora.category.CategoryRepo;
import com.ecom.cartora.product.dto.ProductRequest;
import com.ecom.cartora.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImageUrlTest {

    @Mock
    private ProductRepo productRepo;
    @Mock
    private CategoryRepo categoryRepo;
    @Mock
    private ProductImageStorageService imageStorageService;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProduct_shouldReturnImageUrl() {
        // Arrange
        Category category = new Category();
        category.setCategoryId(4L);
        ProductRequest request = request("https://images.example.test/lamp.jpg");
        when(productRepo.existsBySku(request.getSku())).thenReturn(false);
        when(categoryRepo.findById(4L)).thenReturn(Optional.of(category));
        when(productRepo.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ProductResponse response = productService.createProduct(request);

        // Assert
        assertThat(response.getImageUrl()).isEqualTo("https://images.example.test/lamp.jpg");
    }

    @Test
    void updateProduct_shouldReplaceImageUrl() {
        // Arrange
        Category category = new Category();
        category.setCategoryId(4L);
        Product existing = new Product();
        existing.setId(8L);
        existing.setCategory(category);
        existing.setImageUrl("https://images.example.test/old.jpg");
        ProductRequest request = request("https://images.example.test/new.jpg");
        when(productRepo.findByIdForUpdate(8L)).thenReturn(Optional.of(existing));
        when(productRepo.existsBySkuAndIdNot(request.getSku(), 8L)).thenReturn(false);
        when(categoryRepo.findById(4L)).thenReturn(Optional.of(category));
        when(productRepo.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ProductResponse response = productService.updateProduct(8L, request);

        // Assert
        assertThat(response.getImageUrl()).isEqualTo("https://images.example.test/new.jpg");
    }

    private ProductRequest request(String imageUrl) {
        ProductRequest request = new ProductRequest();
        request.setName("Desk Lamp");
        request.setDescription("Reading lamp");
        request.setPrice(new BigDecimal("1499.00"));
        request.setStockQuantity(4);
        request.setSku("LAMP-001");
        request.setCategoryId(4L);
        request.setImageUrl(imageUrl);
        return request;
    }
}
