package com.ecom.cartora.product;
import com.ecom.cartora.category.Category;
import com.ecom.cartora.category.CategoryRepo;
import com.ecom.cartora.exception.CategoryNotFoundException;
import com.ecom.cartora.exception.ProductAlreadyExistsException;
import com.ecom.cartora.exception.ProductNotFoundException;
import com.ecom.cartora.product.dto.ProductRequest;
import com.ecom.cartora.product.dto.ProductResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {
    @Autowired
    private ProductRepo repo;
    @Autowired
    private CategoryRepo categoryRepo;


    private ProductResponse toResponse(Product product){
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setStockQuantity(product.getStockQuantity());
        response.setSku(product.getSku());
        response.setCategoryId(product.getCategory().getCategoryId());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }
    public ProductResponse createProduct(ProductRequest request){

        if(repo.existsBySku(request.getSku())){
            throw new ProductAlreadyExistsException("SKU already exists");
        }
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setSku(request.getSku());
        Category category = categoryRepo.findById(request.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));
        product.setCategory(category);

        LocalDateTime now = LocalDateTime.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        Product saved = repo.save(product);

        return toResponse(saved);

    }

    public ProductResponse getProductById(Long id){
        Product product =  repo.findById(id).orElseThrow(() -> new ProductNotFoundException("Product not found"));

        return toResponse(product);

    }
    public List<ProductResponse> getAllProducts(){
        List<Product> allProducts = repo.findAll();
        List<ProductResponse> responses = new ArrayList<>();
        for(Product product : allProducts){
            responses.add(toResponse(product));
        }
        return responses;

    }

    public ProductResponse updateProduct(Long id,ProductRequest request){
        Product existing = repo.findById(id).orElseThrow(() -> new ProductNotFoundException("Product not found"));
        if(repo.existsBySkuAndIdNot(request.getSku(),id)){
            throw new ProductAlreadyExistsException("SKU already Exists");
        }

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setStockQuantity(request.getStockQuantity());
        existing.setSku(request.getSku());
        Category category = categoryRepo.findById(request.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));

        existing.setCategory(category);
        existing.setUpdatedAt(LocalDateTime.now());

        Product updated = repo.save(existing);
        return toResponse(updated);

    }
    public void deleteProduct(Long id){
        Product product = repo.findById(id).orElseThrow(() -> new ProductNotFoundException("Product not found"));
        repo.delete(product);
    }

}
