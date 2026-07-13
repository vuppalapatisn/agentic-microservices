package com.amol.microservices.product.repository;

import com.amol.microservices.product.entity.Product;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends CrudRepository<Product,Long> {

    List<Product> findAll();

    /**
     * Keyword + optional category search over the catalog. A null term or category is treated as
     * "match anything" so the same query backs a keyword search, a category filter, or both.
     */
    @Query("SELECT p FROM Product p WHERE "
            + "(CAST(:q AS string) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "AND (CAST(:category AS string) IS NULL OR LOWER(p.category) = LOWER(:category)) "
            + "ORDER BY p.rating DESC")
    List<Product> search(@Param("q") String q, @Param("category") String category);
}
