package com.amol.microservices.ecommerce.entity;

/**
 * Generic error body for client-side validation failures (HTTP 400).
 */
public record ErrorResponse(String error, String message) {
}
