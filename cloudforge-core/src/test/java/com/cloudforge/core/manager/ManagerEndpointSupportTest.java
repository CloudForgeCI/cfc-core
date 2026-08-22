package com.cloudforge.core.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagerEndpointSupportTest {

    @AfterEach
    void clear() {
        System.clearProperty(ManagerEnvKeys.LOCALSTACK_ENDPOINT);
        System.clearProperty(ManagerEnvKeys.AWS_ENDPOINT_URL);
        System.clearProperty(ManagerEnvKeys.MINISTACK_ENDPOINT);
        System.clearProperty(ManagerEnvKeys.AWS_DEFAULT_REGION);
    }

    @Test
    void defaultsToLocalhostGateway() {
        assertEquals("http://localhost:4566", ManagerEndpointSupport.resolveLocalStackEndpoint());
        assertEquals("http://localhost:4566", ManagerEndpointSupport.resolveMiniStackEndpoint());
    }

    @Test
    void prefersSystemPropertiesWhenEnvUnset() {
        System.setProperty(ManagerEnvKeys.LOCALSTACK_ENDPOINT, "http://127.0.0.1:4566");
        System.setProperty(ManagerEnvKeys.MINISTACK_ENDPOINT, "http://127.0.0.1:4567");
        assertEquals("http://127.0.0.1:4566", ManagerEndpointSupport.resolveLocalStackEndpoint());
        assertEquals("http://127.0.0.1:4567", ManagerEndpointSupport.resolveMiniStackEndpoint());
    }
}
