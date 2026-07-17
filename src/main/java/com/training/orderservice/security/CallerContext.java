package com.training.orderservice.security;

/**
 * Stand-in for the authenticated caller identity an API Gateway/JWT would supply.
 * Populated from request headers (X-Customer-Id, X-User-Role) until real gateway
 * auth is wired in.
 */
public record CallerContext(Long customerId, String role) {


    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
