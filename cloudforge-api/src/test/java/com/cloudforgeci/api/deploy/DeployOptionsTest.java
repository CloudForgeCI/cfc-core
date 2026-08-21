package com.cloudforgeci.api.deploy;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The CLI's real code path — {@code InteractiveDeployer.deployLocalTarget} — always calls {@link
 * DeployOptions#defaults()} and has no cross-account concept at all; cloudforge-manager's
 * cross-account deploy feature only ever calls {@link DeployOptions#withCredentialsOverride}
 * additively. This guards that the shared record itself keeps that separation, regardless of
 * whatever else changes around it.
 */
class DeployOptionsTest {

    @Test
    void defaultsHaveNoCredentialsOverride() {
        assertNull(DeployOptions.defaults().credentialsOverride());
    }

    @Test
    void withoutCatalogDoesNotIntroduceACredentialsOverride() {
        assertNull(DeployOptions.defaults().withoutCatalog().credentialsOverride());
    }

    @Test
    void withCredentialsOverrideDoesNotMutateTheOriginalInstance() {
        DeployOptions original = DeployOptions.defaults();
        AwsCredentialsProvider fake = () -> null;

        DeployOptions withOverride = original.withCredentialsOverride(fake);

        assertSame(fake, withOverride.credentialsOverride());
        assertNull(original.credentialsOverride());
    }
}
