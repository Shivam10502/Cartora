package com.ecom.cartora.category;

import com.ecom.cartora.category.dto.CategoryRequest;
import com.ecom.cartora.category.dto.CategoryResponse;
import com.ecom.cartora.exception.CategoryAlreadyExistsException;
import com.ecom.cartora.exception.CategoryNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



@Service
public class CategoryService {

    @Autowired
    private CategoryRepo repo;

    private CategoryResponse toResponse(Category category){
        CategoryResponse response = new CategoryResponse();
        response.setCategoryId(category.getCategoryId());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setCreatedAt(category.getCreatedAt());
        response.setUpdatedAt(category.getUpdatedAt());

        return response;
    }

    public CategoryResponse createCategory(CategoryRequest request){

        if(repo.existsByName(request.getName())){
            throw new CategoryAlreadyExistsException("Category name already exists");
        }
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        LocalDateTime now = LocalDateTime.now();

        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        Category savedCategory = repo.save(category);



        return toResponse(savedCategory);
    }

    public CategoryResponse getCategoryById(Long id){

        Category category =
        repo.findById(id)
               .orElseThrow(() ->new CategoryNotFoundException("Category not found"));

        return toResponse(category);
    }

    public List<CategoryResponse> getAllCategories(){

        List<Category>  allCategory=  repo.findAll();
        List<CategoryResponse> responses = new ArrayList<>();
        for(Category category : allCategory){

            responses.add(toResponse(category));
        }
        return responses;
    }

    public CategoryResponse updateCategory(Long id,CategoryRequest request){

        Category category = repo.findById(id)
                .orElseThrow(() ->new CategoryNotFoundException("Category not found"));

        if (repo.existsByNameAndCategoryIdNot(
                request.getName(),
                id)) {
            throw new CategoryAlreadyExistsException(
                    "Category name already exists"
            );
        }
       category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setUpdatedAt(LocalDateTime.now());

        Category updatedCategory = repo.save(category);

        return toResponse(updatedCategory);
    }

    public void deleteCategory(Long id){
        Category category = repo.findById(id).orElseThrow(() -> new CategoryNotFoundException("Category not found"));

        repo.delete(category);
    }
}
