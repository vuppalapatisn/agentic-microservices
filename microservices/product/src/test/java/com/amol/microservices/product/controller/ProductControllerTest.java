package com.amol.microservices.product.controller;

import com.amol.microservices.product.entity.Product;
import com.amol.microservices.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    void searchProducts_returnsOkWithResults() throws Exception {
        when(productService.search("phone", null)).thenReturn(List.of(new Product()));

        mockMvc.perform(get("/products/search").param("q", "phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray());
    }

    @Test
    void searchProducts_missingParamsReturns400() throws Exception {
        when(productService.search(null, null))
                .thenThrow(new IllegalArgumentException("At least one of 'q' or 'category' must be provided"));

        mockMvc.perform(get("/products/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }
}
