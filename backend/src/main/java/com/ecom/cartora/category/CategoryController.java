package com.ecom.cartora.category;

import com.ecom.cartora.category.dto.CategoryRequest;
import com.ecom.cartora.category.dto.CategoryResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private CategoryService service;


    @PostMapping
    public CategoryResponse addCategory(@Valid @RequestBody CategoryRequest request){
        return service.createCategory(request);
    }

    @GetMapping("/{id}")
    public CategoryResponse getCategory(@PathVariable Long id){

        return service.getCategoryById(id);
    }
    @GetMapping
    public List<CategoryResponse> getAllCategories(){
        return service.getAllCategories();
    }

    @PutMapping("/{id}")
    public CategoryResponse updateCategory(@PathVariable Long id,
                                   @Valid @RequestBody CategoryRequest request){

        return service.updateCategory(id,request);
    }

    @DeleteMapping("/{id}")
    public void deleteCategory(@PathVariable Long id){
         service.deleteCategory(id);
    }

}
