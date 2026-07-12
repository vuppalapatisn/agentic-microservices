package com.amol.microservices.product.controller;

import com.amol.microservices.product.entity.ProductResponse;
import com.amol.microservices.product.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Amol Limaye
 **/
@RestController
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public ProductResponse getAllProducts(){
        return new ProductResponse(productService.getAllProducts());
    }

    @GetMapping("/products/search")
    public ProductResponse searchProducts(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) String category){
        return new ProductResponse(productService.search(q, category));
    }
}
