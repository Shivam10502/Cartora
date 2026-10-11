package com.ecom.cartora.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepo extends JpaRepository<Product,Long>,
        JpaSpecificationExecutor<Product> {
    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);


}
