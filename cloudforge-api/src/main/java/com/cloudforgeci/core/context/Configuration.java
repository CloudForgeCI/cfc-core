package com.cloudforgeci.core.context;

public record Configuration(
        String subdomain,
        String domain,
        Boolean ssl

) {
}
