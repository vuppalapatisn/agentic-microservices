package com.amol.microservices.product.entity;

/**
 * Simple error body returned for client-side validation failures (HTTP 400).
 */
public record ErrorResponse(String error, String message) {
}
