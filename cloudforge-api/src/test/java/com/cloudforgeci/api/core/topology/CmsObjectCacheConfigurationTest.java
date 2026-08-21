package com.cloudforgeci.api.core.topology;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code createRedisReplicationGroup}'s {@code numReplicas} guard — previously flowed straight
 *  into {@code numCacheClusters} with no validation despite the javadoc's documented range,
 *  failing only at CDK synth/deploy time with a far less clear ElastiCache error. The guard runs
 *  before {@code ctx}/{@code spec} are touched, so these tests exercise it directly with null
 *  stand-ins rather than standing up a full CDK stack — the same reasoning {@code
 *  OidcPathShadowingTest} uses to avoid this package's own heavier CDK-synthesis tests. */
class CmsObjectCacheConfigurationTest {

    @ParameterizedTest
    @ValueSource(ints = {-1, -5, 6, 100})
    void rejectsNumReplicasOutsideZeroToFive(int numReplicas) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> CmsObjectCacheConfiguration.createRedisReplicationGroup(null, null, numReplicas));
        assertTrue(e.getMessage().contains("numReplicas"));
    }
}
