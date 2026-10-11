
package com.ecom.cartora.product;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> withFilters(
            String search,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice) {

        return (root, query, cb) -> {
            var predicates = cb.conjunction();

            if (search != null && !search.isBlank()) {
                String pattern = "%"
                        + search.trim().toLowerCase()
                        + "%";

                var nameMatch = cb.like(
                        cb.lower(root.get("name")),
                        pattern
                );

                var descriptionMatch = cb.like(
                        cb.lower(root.get("description")),
                        pattern
                );

                predicates = cb.and(
                        predicates,
                        cb.or(nameMatch, descriptionMatch)
                );
            }

            if (categoryId != null) {
                predicates = cb.and(
                        predicates,
                        cb.equal(
                                root.get("category").get("categoryId"),
                                categoryId
                        )
                );
            }

            if (minPrice != null) {
                predicates = cb.and(
                        predicates,
                        cb.greaterThanOrEqualTo(
                                root.get("price"),
                                minPrice
                        )
                );
            }

            if (maxPrice != null) {
                predicates = cb.and(
                        predicates,
                        cb.lessThanOrEqualTo(
                                root.get("price"),
                                maxPrice
                        )
                );
            }

            return predicates;
        };
    }
}
