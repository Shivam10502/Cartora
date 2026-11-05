package com.ecom.cartora.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminSalesPoint(LocalDate date, BigDecimal revenue) {
}
