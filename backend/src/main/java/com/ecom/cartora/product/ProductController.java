
package com.ecom.cartora.product;

import com.ecom.cartora.product.dto.ProductPageResponse;
import com.ecom.cartora.product.dto.ProductRequest;
import com.ecom.cartora.product.dto.ProductResponse;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;


@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService service;

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse uploadProductImage(@PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file) {
        return service.updateProductImage(id, file);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse addProduct(
            @Valid @RequestBody ProductRequest request) {

        return service.createProduct(request);
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(
            @PathVariable("id") Long id) {

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
            @PathVariable("id") Long id,
            @Valid @RequestBody ProductRequest request) {

        return service.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") Long id) {
        service.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
