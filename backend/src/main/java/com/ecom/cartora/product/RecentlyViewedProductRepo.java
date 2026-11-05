package com.ecom.cartora.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RecentlyViewedProductRepo extends JpaRepository<RecentlyViewedProduct, Long> {

    @Modifying
    @Query(value = "insert into recently_viewed_product (user_id, product_id, viewed_at) "
            + "values (:userId, :productId, :viewedAt) "
            + "on conflict (user_id, product_id) do update set viewed_at = excluded.viewed_at",
            nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("productId") Long productId,
            @Param("viewedAt") LocalDateTime viewedAt);

    @Modifying
    @Query(value = "delete from recently_viewed_product where user_id = :userId and id not in "
            + "(select id from recently_viewed_product where user_id = :userId "
            + "order by viewed_at desc, id desc limit 5)", nativeQuery = true)
    int deleteOlderViews(@Param("userId") Long userId);

    @Query("select viewed from RecentlyViewedProduct viewed "
            + "join fetch viewed.product product join fetch product.category "
            + "where viewed.user.id = :userId order by viewed.viewedAt desc, viewed.id desc")
    List<RecentlyViewedProduct> findRecentProducts(@Param("userId") Long userId);
}
