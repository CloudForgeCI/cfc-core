package com.cloudforgeci.api.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OidcAuthenticationFactory#publicPatternShadowsProtectedPattern}, tested directly against
 * the pure glob-overlap logic rather than through a full CDK stack synthesis (see {@code
 * OidcAuthenticationFactoryTest}'s own {@code @Disabled} tests for why that path is avoided here —
 * this is the same regression the review flagged: a broad public pattern registered at higher ALB
 * listener priority than a protected one silently shadows it, exposing an "authenticated" route
 * without auth. {@code calculateEffectiveProtectedPaths}' own public-path removal is an exact-
 * string {@code Set.removeAll}, which does not catch this glob-containment case on its own.
 */
class OidcPathShadowingTest {

    private static boolean shadows(String publicPattern, String protectedPattern) throws Exception {
        Method m = OidcAuthenticationFactory.class.getDeclaredMethod(
            "publicPatternShadowsProtectedPattern", String.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, publicPattern, protectedPattern);
    }

    @Test
    void aBroadPublicWildcardShadowsANarrowerProtectedPattern() throws Exception {
        assertTrue(shadows("/*", "/admin/*"));
        assertTrue(shadows("/*", "/wp-login.php"));
    }

    @Test
    void aPhpFrontControllerPublicPatternShadowsAProtectedPathUnderIt() throws Exception {
        assertTrue(shadows("/index.php/*", "/index.php/wp-admin/*"));
    }

    @Test
    void disjointPatternsDoNotShadowEachOther() throws Exception {
        assertFalse(shadows("/health", "/admin/*"));
        assertFalse(shadows("/index.php/*", "/wp-admin/*"));
        assertFalse(shadows("/assets/*", "/wp-login.php"));
    }

    @Test
    void anExactDuplicateLiteralPathCountsAsShadowingToo() throws Exception {
        // The case calculateEffectiveProtectedPaths' Set.removeAll already handles by removing it
        // from the protected list outright — still correctly "shadows" if it somehow survived.
        assertTrue(shadows("/webhook", "/webhook"));
    }
}
