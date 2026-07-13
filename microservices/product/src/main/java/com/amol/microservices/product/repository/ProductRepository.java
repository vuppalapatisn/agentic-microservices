package com.amol.microservices.product.repository;

import com.amol.microservices.product.entity.Product;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends CrudRepository<Product,Long> {

    List<Product> findAll();

    /**
     * Keyword + optional category search over the catalog. A null pattern or category is treated as
     * "match anything" so the same query backs a keyword search, a category filter, or both.
     *
     * <p>{@code pattern} is a pre-lowercased SQL LIKE pattern (e.g. {@code "%phone%"}) or null, and
     * {@code category} is a pre-lowercased exact value or null. The pattern is built in the service
     * rather than with {@code CONCAT} in SQL: PostgreSQL can't infer the type of a bind parameter
     * inside {@code '%'||?||'%'} and fails with {@code function lower(bytea) does not exist}. The
     * {@code CAST(... AS string)} guards keep the {@code IS NULL} checks typed as well.
     */
    @Query("SELECT p FROM Product p WHERE "
            + "(CAST(:pattern AS string) IS NULL OR LOWER(p.name) LIKE :pattern "
            + "OR LOWER(p.description) LIKE :pattern "
            + "OR LOWER(p.brand) LIKE :pattern) "
            + "AND (CAST(:category AS string) IS NULL OR LOWER(p.category) = :category) "
            + "ORDER BY p.rating DESC")
    List<Product> search(@Param("pattern") String pattern, @Param("category") String category);
}
