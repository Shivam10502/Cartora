
package com.ecom.cartora.product;

import com.ecom.cartora.category.Category;
import com.ecom.cartora.category.CategoryRepo;
import com.ecom.cartora.exception.CategoryNotFoundException;
import com.ecom.cartora.exception.ProductAlreadyExistsException;
import com.ecom.cartora.exception.ProductNotFoundException;
import com.ecom.cartora.product.dto.ProductPageResponse;
import com.ecom.cartora.product.dto.ProductRequest;
import com.ecom.cartora.product.dto.ProductResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ProductService {

        @Autowired
        private ProductRepo repo;

        @Autowired
        private CategoryRepo categoryRepo;

        @Autowired
        private ProductImageStorageService imageStorageService;

        private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "price", "createdAt");

        private ProductResponse toResponse(Product product) {
                ProductResponse response = new ProductResponse();

                response.setId(product.getId());
                response.setName(product.getName());
                response.setDescription(product.getDescription());
                response.setImageUrl(product.getImageUrl());
                response.setPrice(product.getPrice());
                response.setStockQuantity(product.getStockQuantity());
                response.setSku(product.getSku());
                response.setCategoryId(
                                product.getCategory().getCategoryId());
                response.setCreatedAt(product.getCreatedAt());
                response.setUpdatedAt(product.getUpdatedAt());

                return response;
        }

        @Transactional
        public ProductResponse createProduct(ProductRequest request) {

                if (repo.existsBySku(request.getSku())) {
                        throw new ProductAlreadyExistsException(
                                        "SKU already exists");
                }

                Product product = new Product();
                product.setName(request.getName());
                product.setDescription(request.getDescription());
                product.setImageUrl(request.getImageUrl());
                product.setPrice(request.getPrice());
                product.setStockQuantity(request.getStockQuantity());
                product.setSku(request.getSku());

                Category category = categoryRepo
                                .findById(request.getCategoryId())
                                .orElseThrow(() -> new CategoryNotFoundException(
                                                "Category not found"));

                product.setCategory(category);

                LocalDateTime now = LocalDateTime.now();
                product.setCreatedAt(now);
                product.setUpdatedAt(now);

                Product saved = repo.save(product);

                return toResponse(saved);
        }

        public ProductResponse getProductById(Long id) {
                Product product = repo.findById(id)
                                .orElseThrow(() -> new ProductNotFoundException(
                                                "Product not found"));

                return toResponse(product);
        }

        public List<ProductResponse> getAllProducts() {
                List<Product> allProducts = repo.findAll();
                List<ProductResponse> responses = new ArrayList<>();

                for (Product product : allProducts) {
                        responses.add(toResponse(product));
                }

                return responses;
        }

        @Transactional
        public ProductResponse updateProduct(
                        Long id,
                        ProductRequest request) {

                Product existing = repo.findByIdForUpdate(id)
                                .orElseThrow(() -> new ProductNotFoundException(
                                                "Product not found"));

                if (repo.existsBySkuAndIdNot(request.getSku(), id)) {
                        throw new ProductAlreadyExistsException(
                                        "SKU already exists");
                }

                String previousImage = existing.getImageUrl();
                existing.setName(request.getName());
                existing.setDescription(request.getDescription());
                existing.setImageUrl(request.getImageUrl());
                existing.setPrice(request.getPrice());
                existing.setStockQuantity(request.getStockQuantity());
                existing.setSku(request.getSku());

                Category category = categoryRepo
                                .findById(request.getCategoryId())
                                .orElseThrow(() -> new CategoryNotFoundException(
                                                "Category not found"));

                existing.setCategory(category);
                existing.setUpdatedAt(LocalDateTime.now());

                Product updated = repo.save(existing);
                if (!java.util.Objects.equals(previousImage, updated.getImageUrl())) {
                        deleteImageAfterCommit(previousImage);
                }

                return toResponse(updated);
        }

        @Transactional
        public ProductResponse updateProductImage(Long id, MultipartFile file) {
                Product existing = repo.findByIdForUpdate(id)
                                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
                String previousImage = existing.getImageUrl();
                String newImage = imageStorageService.store(file);
                try {
                        existing.setImageUrl(newImage);
                        existing.setUpdatedAt(LocalDateTime.now());
                        Product saved = repo.save(existing);
                        if (TransactionSynchronizationManager.isSynchronizationActive()) {
                                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                        @Override
                                        public void afterCommit() {
                                                imageStorageService.deleteManagedImage(previousImage);
                                        }
                                });
                        }
                        return toResponse(saved);
                } catch (RuntimeException ex) {
                        imageStorageService.deleteManagedImage(newImage);
                        throw ex;
                }
        }

        @Transactional
        public void deleteProduct(Long id) {
                Product product = repo.findById(id)
                                .orElseThrow(() -> new ProductNotFoundException(
                                                "Product not found"));

                repo.delete(product);
                deleteImageAfterCommit(product.getImageUrl());
        }

        private void deleteImageAfterCommit(String imageUrl) {
                if (imageUrl == null) return;
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                        imageStorageService.deleteManagedImage(imageUrl);
                        return;
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                                imageStorageService.deleteManagedImage(imageUrl);
                        }
                });
        }

        public ProductPageResponse searchProducts(
                        String search,
                        Long categoryId,
                        BigDecimal minPrice,
                        BigDecimal maxPrice,
                        int page,
                        int size,
                        String sortBy,
                        String direction) {

                // Validate pagination.
                if (page < 0) {
                        throw badRequest(
                                        "Page number cannot be negative");
                }

                if (size < 1 || size > 100) {
                        throw badRequest(
                                        "Page size must be between 1 and 100");
                }

                // Validate price range.
                if (minPrice != null
                                && minPrice.compareTo(BigDecimal.ZERO) < 0) {
                        throw badRequest(
                                        "Minimum price cannot be negative");
                }

                if (maxPrice != null
                                && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
                        throw badRequest(
                                        "Maximum price cannot be negative");
                }

                if (minPrice != null
                                && maxPrice != null
                                && minPrice.compareTo(maxPrice) > 0) {
                        throw badRequest(
                                        "Minimum price cannot exceed maximum price");
                }

                if (categoryId != null && categoryId < 1) {
                        throw badRequest(
                                        "Category ID must be positive");
                }

                // Validate sort field.
                if (sortBy == null
                                || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
                        throw badRequest(
                                        "sortBy must be name, price, or createdAt");
                }

                if (direction == null
                                || !(direction.equalsIgnoreCase("asc")
                                                || direction.equalsIgnoreCase("desc"))) {
                        throw badRequest(
                                        "direction must be asc or desc");
                }

                Sort.Direction sortDirection = direction.equalsIgnoreCase("asc")
                                ? Sort.Direction.ASC
                                : Sort.Direction.DESC;

                // Use ID as a tie-breaker for stable ordering.
                Sort sort = Sort.by(sortDirection, sortBy)
                                .and(Sort.by(Sort.Direction.ASC, "id"));

                Pageable pageable = PageRequest.of(
                                page,
                                size,
                                sort);

                var specification = ProductSpecification.withFilters(
                                search,
                                categoryId,
                                minPrice,
                                maxPrice);

                Page<Product> productPage = repo.findAll(specification, pageable);

                List<ProductResponse> content = productPage
                                .getContent()
                                .stream()
                                .map(this::toResponse)
                                .toList();

                return new ProductPageResponse(
                                content,
                                productPage.getNumber(),
                                productPage.getSize(),
                                productPage.getTotalElements(),
                                productPage.getTotalPages(),
                                productPage.isFirst(),
                                productPage.isLast());
        }

        private ResponseStatusException badRequest(
                        String message) {

                return new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                message);
        }
}
