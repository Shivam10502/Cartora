package com.ecom.cartora.admin;

import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.product.ProductRepo;
import com.ecom.cartora.payment.PaymentRefundRepo;
import com.ecom.cartora.payment.entity.RefundStatus;
import com.ecom.cartora.user.Role;
import com.ecom.cartora.user.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class AdminDashboardService {

    private final UserRepo userRepo;
    private final ProductRepo productRepo;
    private final OrderRepo orderRepo;
    private final PaymentRefundRepo paymentRefundRepo;

    public AdminDashboardService(UserRepo userRepo, ProductRepo productRepo, OrderRepo orderRepo,
            PaymentRefundRepo paymentRefundRepo) {
        this.userRepo = userRepo;
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
        this.paymentRefundRepo = paymentRefundRepo;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getOverview() {
        BigDecimal revenue = orderRepo.sumRevenueByStatuses(List.of(
                OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED));
        BigDecimal refunds = paymentRefundRepo.sumRefundsByStatus(RefundStatus.PROCESSED);
        revenue = (revenue == null ? BigDecimal.ZERO : revenue)
                .subtract(refunds == null ? BigDecimal.ZERO : refunds);
        return new AdminDashboardResponse(
                userRepo.countByRole(Role.CUSTOMER),
                productRepo.count(),
                productRepo.countByStockQuantityLessThanEqual(5),
                orderRepo.countByStatus(OrderStatus.PENDING),
                orderRepo.countByStatus(OrderStatus.CONFIRMED) + orderRepo.countByStatus(OrderStatus.SHIPPED),
                revenue.max(BigDecimal.ZERO));
    }

    @Transactional(readOnly = true)
    public List<AdminSalesPoint> getSales(int days) {
        if (days < 1 || days > 90) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Days must be between 1 and 90");
        }
        return orderRepo.findDailyRevenueSince(LocalDateTime.now().minusDays(days - 1L)).stream()
                .map(point -> new AdminSalesPoint(point.getSaleDate(), point.getRevenue()))
                .toList();
    }
}
