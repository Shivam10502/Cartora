
package com.ecom.cartora.product;

import com.ecom.cartora.category.Category;
import com.ecom.cartora.exception.CategoryNotFoundException;
import com.ecom.cartora.exception.ProductAlreadyExistsException;
import com.ecom.cartora.exception.ProductNotFoundException;
import com.ecom.cartora.product.dto.ProductPageResponse;
import com.ecom.cartora.product.dto.ProductRequest;
import com.ecom.cartora.product.dto.ProductResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

        @Mock
        private ProductRepo productRepo;

        @Mock
        private com.ecom.cartora.category.CategoryRepo categoryRepo;

        @Mock
        private ProductImageStorageService imageStorageService;

        @InjectMocks
        private ProductService productService;

        private Product product;
        private Category category;

        @BeforeEach
        void setUp() {
                category = new Category();
                category.setCategoryId(1L);

                product = new Product();
                product.setId(1L);
                product.setName("Mechanical Keyboard");
                product.setDescription("RGB mechanical keyboard");
                product.setPrice(new BigDecimal("2499.00"));
                product.setStockQuantity(5);
                product.setSku("KEYBOARD-CARTORA-001");
                product.setCategory(category);
        }

        @Test
        void getProductById_shouldReturnProduct_whenProductExists() {

                // Arrange
                when(productRepo.findById(1L))
                                .thenReturn(Optional.of(product));

                // Act
                ProductResponse response = productService.getProductById(1L);

                // Assert
                assertNotNull(response);
                assertEquals(1L, response.getId());
                assertEquals("Mechanical Keyboard", response.getName());
                assertEquals(
                                new BigDecimal("2499.00"),
                                response.getPrice());

                verify(productRepo).findById(1L);
        }

        @Test
        void getProductById_shouldThrowException_whenProductDoesNotExist() {

                // Arrange
                when(productRepo.findById(999L))
                                .thenReturn(Optional.empty());

                // Act + Assert
                assertThrows(
                                ProductNotFoundException.class,
                                () -> productService.getProductById(999L));

                verify(productRepo).findById(999L);
        }

        @Test
        void deleteProduct_shouldRemoveManagedImageAfterDeletingProduct() {
                product.setImageUrl("/uploads/managed-image.jpg");
                when(productRepo.findById(1L)).thenReturn(Optional.of(product));

                productService.deleteProduct(1L);

                verify(productRepo).delete(product);
                verify(imageStorageService).deleteManagedImage("/uploads/managed-image.jpg");
        }

        @Test
        void updateProduct_shouldRemovePreviousManagedImageWhenImageChanges() {
                product.setImageUrl("/uploads/old-image.png");
                when(productRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
                when(productRepo.existsBySkuAndIdNot("KEYBOARD-CARTORA-001", 1L)).thenReturn(false);
                when(categoryRepo.findById(1L)).thenReturn(Optional.of(category));
                when(productRepo.save(product)).thenReturn(product);
                ProductRequest request = new ProductRequest("Mechanical Keyboard", "RGB mechanical keyboard",
                        "https://images.example.test/new.png", new BigDecimal("2499.00"), 5,
                        "KEYBOARD-CARTORA-001", 1L);

                productService.updateProduct(1L, request);

                verify(imageStorageService).deleteManagedImage("/uploads/old-image.png");
        }

        @Test
        void createProduct_shouldCreateProduct_whenRequestIsValid() {

                // Arrange
                ProductRequest request = new ProductRequest();
                request.setName("Mechanical Keyboard");
                request.setDescription("RGB mechanical keyboard");
                request.setPrice(new BigDecimal("2499.00"));
                request.setStockQuantity(5);
                request.setSku("KEYBOARD-CARTORA-001");
                request.setCategoryId(1L);

                when(productRepo.existsBySku(request.getSku()))
                                .thenReturn(false);

                when(categoryRepo.findById(1L))
                                .thenReturn(Optional.of(category));

                // Return the same product passed to save().
                when(productRepo.save(any(Product.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // Act
                ProductResponse response = productService.createProduct(request);

                // Assert
                assertNotNull(response);
                assertEquals("Mechanical Keyboard", response.getName());
                assertEquals(
                                new BigDecimal("2499.00"),
                                response.getPrice());
                assertEquals(1L, response.getCategoryId());

                verify(productRepo).save(any(Product.class));
        }

        @Test
        void createProduct_shouldThrowException_whenSkuAlreadyExists() {

                // Arrange
                ProductRequest request = new ProductRequest();
                request.setName("Mechanical Keyboard");
                request.setDescription("RGB mechanical keyboard");
                request.setPrice(new BigDecimal("2499.00"));
                request.setStockQuantity(5);
                request.setSku("KEYBOARD-CARTORA-001");
                request.setCategoryId(1L);

                when(productRepo.existsBySku(request.getSku()))
                                .thenReturn(true);

                // Act + Assert
                assertThrows(
                                ProductAlreadyExistsException.class,
                                () -> productService.createProduct(request));

                // Product creation must stop before category lookup or save.
                verify(categoryRepo, never()).findById(any());
                verify(productRepo, never()).save(any(Product.class));
        }

        @Test
        void createProduct_shouldThrowException_whenCategoryDoesNotExist() {

                // Arrange
                ProductRequest request = new ProductRequest();
                request.setName("Mechanical Keyboard");
                request.setDescription("RGB mechanical keyboard");
                request.setPrice(new BigDecimal("2499.00"));
                request.setStockQuantity(5);
                request.setSku("KEYBOARD-CARTORA-001");
                request.setCategoryId(999L);

                when(productRepo.existsBySku(request.getSku()))
                                .thenReturn(false);

                when(categoryRepo.findById(999L))
                                .thenReturn(Optional.empty());

                // Act + Assert
                assertThrows(
                                CategoryNotFoundException.class,
                                () -> productService.createProduct(request));

                verify(productRepo, never()).save(any(Product.class));
        }

        @Test
        void searchProducts_shouldReturnPageMetadataAndRequestedSorting() {

                // Arrange
                PageImpl<Product> productPage = new PageImpl<>(
                                List.of(product),
                                PageRequest.of(1, 5),
                                11);
                when(productRepo.findAll(
                                org.mockito.ArgumentMatchers.<Specification<Product>>any(),
                                any(Pageable.class)))
                                .thenReturn(productPage);

                // Act
                ProductPageResponse response = productService.searchProducts(
                                "keyboard",
                                1L,
                                new BigDecimal("1000.00"),
                                new BigDecimal("3000.00"),
                                1,
                                5,
                                "price",
                                "desc");

                // Assert
                assertThat(response.getContent())
                                .extracting(ProductResponse::getId)
                                .containsExactly(1L);
                assertThat(response.getPage()).isEqualTo(1);
                assertThat(response.getSize()).isEqualTo(5);
                assertThat(response.getTotalElements()).isEqualTo(11);
                assertThat(response.getTotalPages()).isEqualTo(3);
                assertThat(response.isFirst()).isFalse();
                assertThat(response.isLast()).isFalse();

                ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
                verify(productRepo).findAll(
                                org.mockito.ArgumentMatchers.<Specification<Product>>any(),
                                pageableCaptor.capture());

                Pageable pageable = pageableCaptor.getValue();
                assertThat(pageable.getPageNumber()).isEqualTo(1);
                assertThat(pageable.getPageSize()).isEqualTo(5);
                assertThat(pageable.getSort().getOrderFor("price").getDirection())
                                .isEqualTo(Sort.Direction.DESC);
                assertThat(pageable.getSort().getOrderFor("id").getDirection())
                                .isEqualTo(Sort.Direction.ASC);
        }

        @Test
        void searchProducts_shouldRejectUnsupportedSortFieldBeforeRepositoryCall() {

                // Arrange
                String unsupportedSortField = "sku";

                // Act + Assert
                assertThatThrownBy(() -> productService.searchProducts(
                                null,
                                null,
                                null,
                                null,
                                0,
                                10,
                                unsupportedSortField,
                                "asc"))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("sortBy");

                verifyNoInteractions(productRepo);
        }

}
