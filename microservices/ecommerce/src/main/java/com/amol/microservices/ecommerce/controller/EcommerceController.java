package com.amol.microservices.ecommerce.controller;

import com.amol.microservices.ecommerce.assembler.ProductAssembler;
import com.amol.microservices.ecommerce.client.CouponClient;
import com.amol.microservices.ecommerce.entity.CouponApplyErrorResponse;
import com.amol.microservices.ecommerce.entity.EcommerceProduct;
import com.amol.microservices.ecommerce.entity.EcommerceProductResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Amol Limaye
 **/
@RestController
public class EcommerceController {

    private static final Pattern COUPON_CODE = Pattern.compile("^[A-Za-z0-9]{6}$");

    @Autowired
    ProductAssembler productAssembler;

    @Autowired
    CouponClient couponClient;

    @GetMapping("/ecommerceProducts")
    public EcommerceProductResponse getAllEcommerceProducts(){
        return new EcommerceProductResponse(productAssembler.getEcommerceProducts());
    }

    @PostMapping(value = "/apply-coupon", consumes = "text/plain")
    public ResponseEntity<?> applyCoupon(@RequestBody String couponCode) {
        if (couponCode == null) {
            return ResponseEntity.badRequest()
                    .body(new CouponApplyErrorResponse("validation_error", "Coupon code is required"));
        }
        String trimmed = couponCode.trim();
        if (!COUPON_CODE.matcher(trimmed).matches()) {
            return ResponseEntity.badRequest()
                    .body(new CouponApplyErrorResponse("validation_error", "Coupon code must be 6 alphanumeric characters"));
        }
        try {
            couponClient.validateCoupon(trimmed);
            return ResponseEntity.ok().build();
        } catch (RestClientException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new CouponApplyErrorResponse("coupon_apply_failed", ex.getMessage()));
        }
    }
}
