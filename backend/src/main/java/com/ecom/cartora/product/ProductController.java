
package com.ecom.cartora.product;

import com.ecom.cartora.product.dto.ProductPageResponse;
import com.ecom.cartora.product.dto.ProductRequest;
import com.ecom.cartora.product.dto.ProductResponse;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;


@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService service;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse addProduct(
            @Valid @RequestBody ProductRequest request) {

        return service.createProduct(request);
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(
            @PathVariable Long id) {

        return service.getProductById(id);
    }

    @GetMapping
    public ProductPageResponse searchProducts(
            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            Long categoryId,

            @RequestParam(required = false)
            BigDecimal minPrice,

            @RequestParam(required = false)
            BigDecimal maxPrice,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(defaultValue = "createdAt")
            String sortBy,

            @RequestParam(defaultValue = "desc")
            String direction) {

        return service.searchProducts(
                search,
                categoryId,
                minPrice,
                maxPrice,
                page,
                size,
                sortBy,
                direction
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        return service.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteProduct(@PathVariable Long id) {
        service.deleteProduct(id);
    }
}
