package com.ecom.cartora.product;

import com.ecom.cartora.product.dto.ProductResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/recently-viewed")
public class RecentlyViewedProductController {

    private final RecentlyViewedProductService service;

    public RecentlyViewedProductController(RecentlyViewedProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductResponse> getRecentProducts() {
        return service.getRecentProducts();
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Void> recordView(@PathVariable("productId") Long productId) {
        service.recordView(productId);
        return ResponseEntity.noContent().build();
    }
}
