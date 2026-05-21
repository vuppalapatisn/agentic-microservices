package com.amol.microservices.ecommerce.client;

import com.amol.microservices.ecommerce.config.ExternalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class CouponClient {

    private static final Logger log = LoggerFactory.getLogger(CouponClient.class);
    private static final String COUPON_VALIDATE_PATH = "/coupons/";

    private final RestTemplate restTemplate;
    private final ExternalConfig externalConfig;

    public CouponClient(RestTemplate restTemplate, ExternalConfig externalConfig) {
        this.restTemplate = restTemplate;
        this.externalConfig = externalConfig;
    }

    public void validateCoupon(String couponCode) {
        String baseUrl = externalConfig.getCouponServiceBaseUrl().replaceAll("/+$", "");
        String targetUrl = baseUrl + COUPON_VALIDATE_PATH + couponCode;
        log.info("coupon_apply_start couponCode={} targetUrl={}", couponCode, targetUrl);
        try {
            restTemplate.exchange(targetUrl, HttpMethod.GET, null, String.class);
        } catch (RestClientException ex) {
            log.error("coupon_apply_failed couponCode={} targetUrl={}", couponCode, targetUrl, ex);
            throw ex;
        }
    }
}
