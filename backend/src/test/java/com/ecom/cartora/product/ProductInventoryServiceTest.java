package com.ecom.cartora.product;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductInventoryServiceTest {

    @Mock private ProductRepo productRepo;
    @Mock private EntityManager entityManager;
    private ProductInventoryService inventoryService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        inventoryService = new ProductInventoryService(productRepo);
        org.springframework.test.util.ReflectionTestUtils.setField(inventoryService, "entityManager", entityManager);
    }

    @Test
    void lockAndRefreshLocksUniqueProductsInStableIdOrder() {
        Product first = new Product();
        first.setId(2L);
        Product second = new Product();
        second.setId(9L);
        when(productRepo.findById(2L)).thenReturn(Optional.of(first));
        when(productRepo.findById(9L)).thenReturn(Optional.of(second));
        doNothing().when(entityManager).refresh(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(LockModeType.PESSIMISTIC_WRITE));

        var locked = inventoryService.lockAndRefresh(List.of(9L, 2L, 9L));

        assertThat(locked).containsEntry(2L, first).containsEntry(9L, second);
        var order = inOrder(productRepo, entityManager);
        order.verify(productRepo).findById(2L);
        order.verify(entityManager).refresh(first, LockModeType.PESSIMISTIC_WRITE);
        order.verify(productRepo).findById(9L);
        order.verify(entityManager).refresh(second, LockModeType.PESSIMISTIC_WRITE);
        verify(productRepo, org.mockito.Mockito.times(1)).findById(9L);
    }
}
