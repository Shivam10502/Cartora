package com.ecom.cartora.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;



@Repository
public interface CategoryRepo extends JpaRepository<Category,Long> {


    boolean existsByName(String name);
    boolean existsByNameAndCategoryIdNot(String name, Long categoryId);
}
