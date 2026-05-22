package com.amol.microservices.product.controller;

import com.amol.microservices.product.entity.ProductResponse;
import com.amol.microservices.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Amol Limaye
 **/
@RestController
public class ProductController {

    @Autowired
    ProductRepository productRepository;

    @GetMapping("/products")
    public ProductResponse getAllProducts(){
        return new ProductResponse(productRepository.findAll());
    }
}
