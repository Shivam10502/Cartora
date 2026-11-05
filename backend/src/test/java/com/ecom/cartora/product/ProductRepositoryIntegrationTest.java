package com.ecom.cartora.product;

import com.ecom.cartora.category.Category;
import com.ecom.cartora.category.CategoryRepo;
import com.ecom.cartora.product.dto.ProductResponse;
import com.ecom.cartora.user.Role;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductRepositoryIntegrationTest {

    @Autowired private ProductRepo productRepo;
    @Autowired private CategoryRepo categoryRepo;
    @Autowired private UserRepo userRepo;
    @Autowired private RecentlyViewedProductService recentlyViewedProductService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void specificationsFilterProductsAndPageWithStablePriceOrdering() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = new Category();
        category.setName("Repo test " + suffix);
        category.setDescription("Repository integration test category");
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        category = categoryRepo.saveAndFlush(category);

        saveProduct(category, "Oak desk lamp " + suffix, "Warm reading light", "10.00", "SKU-A-" + suffix);
        saveProduct(category, "Oak floor lamp " + suffix, "Living room light", "20.00", "SKU-B-" + suffix);
        saveProduct(category, "Oak night light " + suffix, "Soft night glow", "30.00", "SKU-C-" + suffix);
        productRepo.flush();

        var specification = ProductSpecification.withFilters("OAK", category.getCategoryId(),
                new BigDecimal("15.00"), new BigDecimal("30.00"));
        var firstPage = productRepo.findAll(specification,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "price").and(Sort.by("id"))));
        var secondPage = productRepo.findAll(specification,
                PageRequest.of(1, 1, Sort.by(Sort.Direction.ASC, "price").and(Sort.by("id"))));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).singleElement()
                .extracting(Product::getPrice).isEqualTo(new BigDecimal("20.00"));
        assertThat(secondPage.getContent()).singleElement()
                .extracting(Product::getPrice).isEqualTo(new BigDecimal("30.00"));
    }

    @Test
    void migrationCreatesUniquePartialIndexForDefaultAddresses() {
        Boolean indexExists = jdbcTemplate.queryForObject(
                "select to_regclass('uk_address_single_default_per_user') is not null", Boolean.class);

        assertThat(indexExists).isTrue();
    }

    @Test
    void recentlyViewedProductsAreAccountScopedDeduplicatedAndLimitedToFive() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = new Category();
        category.setName("Recent test " + suffix);
        category.setDescription("Recently viewed integration test category");
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        category = categoryRepo.saveAndFlush(category);

        User user = new User();
        user.setName("Recent test customer");
        user.setEmail("recent-" + suffix + "@cartora.test");
        user.setPassword("not-a-login-password");
        user.setRole(Role.CUSTOMER);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user = userRepo.saveAndFlush(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "ignored"));

        java.util.List<Product> products = new java.util.ArrayList<>();
        for (int index = 0; index < 6; index++) {
            products.add(saveProduct(category, "Recent product " + index + " " + suffix,
                    "recently viewed integration test", Integer.toString(10 + index),
                    "RECENT-" + index + "-" + suffix));
        }

        products.forEach(product -> recentlyViewedProductService.recordView(product.getId()));
        var firstHistory = recentlyViewedProductService.getRecentProducts();
        recentlyViewedProductService.recordView(products.get(3).getId());
        var updatedHistory = recentlyViewedProductService.getRecentProducts();

        assertThat(firstHistory).hasSize(5);
        assertThat(firstHistory).extracting(ProductResponse::getId)
                .containsExactly(products.get(5).getId(), products.get(4).getId(),
                        products.get(3).getId(), products.get(2).getId(), products.get(1).getId());
        assertThat(updatedHistory).hasSize(5);
        assertThat(updatedHistory).extracting(ProductResponse::getId)
                .containsExactly(products.get(3).getId(), products.get(5).getId(),
                        products.get(4).getId(), products.get(2).getId(), products.get(1).getId());
    }

    private Product saveProduct(Category category, String name, String description, String price, String sku) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(4);
        product.setSku(sku);
        product.setCategory(category);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return productRepo.save(product);
    }
}
