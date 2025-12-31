package com.cloudforge.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Load balancer type for application ingress.
 *
 * <h2>Configuration</h2>
 * Set via deployment context:
 * <pre>{@code
 * cfc.put("lbType", "alb");  // Application Load Balancer (default)
 * cfc.put("lbType", "nlb");  // Network Load Balancer
 * }</pre>
 *
 * <h2>Types</h2>
 * <ul>
 *   <li><b>ALB</b> - Application Load Balancer (Layer 7, HTTP/HTTPS, OIDC support)</li>
 *   <li><b>NLB</b> - Network Load Balancer (Layer 4, TCP/UDP, ultra-low latency)</li>
 * </ul>
 *
 * <h2>Feature Comparison</h2>
 * <table>
 *   <caption>ALB vs NLB Features</caption>
 *   <tr><th>Feature</th><th>ALB</th><th>NLB</th></tr>
 *   <tr><td>OIDC Authentication</td><td>✓</td><td>✗</td></tr>
 *   <tr><td>WAF Integration</td><td>✓</td><td>✗</td></tr>
 *   <tr><td>Path-based routing</td><td>✓</td><td>✗</td></tr>
 *   <tr><td>Static IP</td><td>✗</td><td>✓</td></tr>
 *   <tr><td>Ultra-low latency</td><td>✗</td><td>✓</td></tr>
 *   <tr><td>TCP/UDP support</td><td>✗</td><td>✓</td></tr>
 * </table>
 */
public enum LoadBalancerType {
    /**
     * Application Load Balancer (Layer 7).
     * Supports HTTP/HTTPS, OIDC authentication, WAF, and path-based routing.
     * Required for ALB-OIDC auth mode.
     */
    ALB("alb"),

    /**
     * Network Load Balancer (Layer 4).
     * Supports TCP/UDP with ultra-low latency and static IPs.
     * Does not support OIDC or WAF integration.
     */
    NLB("nlb");

    private final String value;

    LoadBalancerType(String value) {
        this.value = value;
    }

    /**
     * Returns the JSON/string value for this load balancer type.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Returns the string representation.
     */
    @Override
    public String toString() {
        return value;
    }

    /**
     * Parse load balancer type from string (case-insensitive).
     *
     * @param value String value from deployment context
     * @return LoadBalancerType enum value
     * @throws IllegalArgumentException if value is not recognized
     */
    @JsonCreator
    public static LoadBalancerType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ALB; // Default
        }

        String normalized = value.trim().toLowerCase();

        for (LoadBalancerType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }

        // Try enum name
        try {
            return LoadBalancerType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown load balancer type '" + value + "'. Valid values: alb, nlb"
            );
        }
    }

    /**
     * Check if this load balancer type supports OIDC authentication.
     */
    public boolean supportsOidc() {
        return this == ALB;
    }

    /**
     * Check if this load balancer type supports WAF integration.
     */
    public boolean supportsWaf() {
        return this == ALB;
    }

    /**
     * Check if this load balancer type supports path-based routing.
     */
    public boolean supportsPathRouting() {
        return this == ALB;
    }

    /**
     * Check if this load balancer type provides static IPs.
     */
    public boolean hasStaticIp() {
        return this == NLB;
    }
}
