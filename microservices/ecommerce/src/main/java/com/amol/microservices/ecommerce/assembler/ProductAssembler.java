package com.amol.microservices.ecommerce.assembler;

import com.amol.microservices.ecommerce.config.ExternalConfig;
import com.amol.microservices.ecommerce.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Amol Limaye
 **/
@Component
public class ProductAssembler {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ExternalConfig externalConfig;

    private static final String PRODUCT_SERVICE_ENDPOINT = "/product-service/products";

    private static final String PRODUCT_SEARCH_ENDPOINT = "/product-service/products/search";

    private static final String IMAGE_SERVICE_ENDPOINT = "/image-service/images";

    public List<EcommerceProduct> getEcommerceProducts(){
        ResponseEntity<ProductResponse> productResponse = restTemplate.exchange(
                getServiceURL(externalConfig.getProductServiceBaseUrl(), PRODUCT_SERVICE_ENDPOINT),
                HttpMethod.GET,null,ProductResponse.class);
        return mergeProductData(productResponse, fetchImages());
    }

    public List<EcommerceProduct> searchEcommerceProducts(String q, String category){
        String searchUrl = UriComponentsBuilder
                .fromHttpUrl(getServiceURL(externalConfig.getProductServiceBaseUrl(), PRODUCT_SEARCH_ENDPOINT))
                .queryParamIfPresent("q", Optional.ofNullable(q))
                .queryParamIfPresent("category", Optional.ofNullable(category))
                .encode()
                .build()
                .toUriString();
        ResponseEntity<ProductResponse> productResponse = restTemplate.exchange(
                searchUrl, HttpMethod.GET, null, ProductResponse.class);
        return mergeProductData(productResponse, fetchImages());
    }

    private ResponseEntity<ImageResponse> fetchImages(){
        if(!externalConfig.getUseImages()) {
            return null;
        }
        return restTemplate.exchange(getServiceURL(externalConfig.getImagesServiceBaseUrl(), IMAGE_SERVICE_ENDPOINT),
                HttpMethod.GET, null, ImageResponse.class);
    }

    private String getServiceURL(String serviceBaseUrl, String serviceEndpoint){
        return new StringBuilder(serviceBaseUrl)
                .append(serviceEndpoint).toString();
    }

    private List<EcommerceProduct> mergeProductData(ResponseEntity<ProductResponse> productResponse, ResponseEntity<ImageResponse> imageResponse){
        List<EcommerceProduct> ecommerceProducts = new ArrayList<>();
        for(Product product:productResponse.getBody().getProducts()){
            EcommerceProduct ecommerceProduct = new EcommerceProduct(product);
            if(imageResponse!=null) {
                Image image = imageResponse.getBody().getImages().
                        stream().filter(i -> product.getProductId() == i.getProductId())
                        .findAny().orElse(null);
                if (image != null) {
                    ecommerceProduct.setImage(image.getPath());
                }
            }
            ecommerceProducts.add(ecommerceProduct);
        }
        return ecommerceProducts;
    }
}
