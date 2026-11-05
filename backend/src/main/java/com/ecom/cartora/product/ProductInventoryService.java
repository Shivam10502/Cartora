package com.ecom.cartora.product;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class ProductInventoryService {

    private final ProductRepo productRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public ProductInventoryService(ProductRepo productRepo) {
        this.productRepo = productRepo;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Product> lockAndRefresh(Collection<Long> productIds) {
        Map<Long, Product> locked = new HashMap<>();
        productIds.stream().distinct().sorted().forEach(id -> {
            Product product = productRepo.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Product no longer exists"));
            // Cart/order item loading may have put this product in the persistence
            // context already. Refresh so the locked row's current stock is used.
            entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
            locked.put(id, product);
        });
        return locked;
    }
}
