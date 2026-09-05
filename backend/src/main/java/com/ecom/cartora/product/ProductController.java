package com.ecom.cartora.product;

import com.ecom.cartora.product.dto.ProductRequest;
import com.ecom.cartora.product.dto.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService service;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse addProduct(@Valid @RequestBody ProductRequest request){
        return service.createProduct(request);
    }
    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id){
        return service.getProductById(id);
    }

    @GetMapping
    public List<ProductResponse> getAllProducts(){
        return service.getAllProducts();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse updateProduct(@PathVariable Long id,@Valid @RequestBody ProductRequest request){
        return service.updateProduct(id,request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteProduct(@PathVariable Long id){
         service.deleteProduct(id);
    }
}
