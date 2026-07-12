package com.amol.microservices.product.service;

import com.amol.microservices.product.entity.Product;
import com.amol.microservices.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Catalog read operations. Search validation lives here so it stays out of transport code and is
 * unit-testable without the web layer.
 */
@Service
public class ProductService {

    static final int MAX_QUERY_LENGTH = 100;

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> search(String query, String category) {
        String q = normalize(query);
        String cat = normalize(category);
        if (q == null && cat == null) {
            throw new IllegalArgumentException("At least one of 'q' or 'category' must be provided");
        }
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("'q' must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        return productRepository.search(q, cat);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
