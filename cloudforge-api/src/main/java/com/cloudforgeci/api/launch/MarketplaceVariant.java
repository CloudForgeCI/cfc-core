package com.cloudforgeci.api.launch;

/**
 * Which of the two AWS Marketplace CloudFormation templates for CloudForge Manager to produce.
 * The choice changes which resources exist, so it is fixed at synth time rather than driven by a
 * launch-form parameter.
 */
public enum MarketplaceVariant {

    /** Cognito sign-in: the launch form requires an admin email and a Cognito domain prefix. */
    SSO,

    /** No Cognito: the first administrator is created with the one-time setup token Manager logs at startup. */
    TOKEN
}
