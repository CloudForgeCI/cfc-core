package com.cloudforge.core.enums;

/**
 * Defines the deployment topology patterns supported by CloudForge.
 *
 * CloudForge 3.0.0 Breaking Changes:
 * - JENKINS_SINGLE_NODE removed (use JENKINS_SERVICE instead)
 * - All new application topologies added
 */
public enum TopologyType {
    // Preserved from 2.x
    JENKINS_SERVICE,
    S3_WEBSITE,

    // CloudForge 3.0.0: Universal application topology
    APPLICATION_SERVICE  // Generic service topology for any ApplicationSpec
}
