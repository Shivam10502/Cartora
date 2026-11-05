package com.ecom.cartora.admin;

import java.math.BigDecimal;

public record AdminDashboardResponse(
        long customerCount,
        long productCount,
        long lowStockProductCount,
        long pendingOrderCount,
        long ordersInFulfilmentCount,
        BigDecimal paidRevenue) {
}
