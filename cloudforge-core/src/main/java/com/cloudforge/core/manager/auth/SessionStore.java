package com.cloudforge.core.manager.auth;

import java.util.Optional;
import java.util.Set;

/**
 * Strategy interface for where Manager's own session cookies live — sibling to {@link
 * AuthBackend} (local-DB-vs-Cognito for the *user directory*; this is in-memory-vs-Redis for
 * *which session cookies this process currently recognizes*, an orthogonal concern). A local,
 * single-instance Manager needs nothing beyond an in-memory map; a horizontally-scaled Manager
 * behind a load balancer needs every instance to recognize a session created by any other
 * instance, which an in-memory store can never do regardless of how the user directory itself is
 * configured — see {@code SessionManager}'s javadoc in cloudforge-manager for the full story.
 *
 * <p>Deliberately framework-agnostic (no Spring, no Redis client types) — implementations live in
 * cloudforge-manager, same split as {@link AuthBackend}.</p>
 */
public interface SessionStore {

    /** Creates a new session for {@code subject}, returning the opaque cookie value. */
    String create(String subject);

    /** Resolves a session cookie to its owning subject, refreshing its TTL (sliding expiry) if
     *  still valid. Empty if the cookie is unknown or has expired. */
    Optional<String> resolveSubject(String sessionId);

    /** Ends one session. No-op if the cookie is already unknown/expired. */
    void invalidate(String sessionId);

    /** Ends every session belonging to {@code subject} (admin force-logout). Returns how many
     *  were actually active and removed. */
    int invalidateAllForSubject(String subject);

    /** True if {@code subject} has at least one active (non-expired) session. */
    boolean isLoggedIn(String subject);

    /** How many active sessions {@code subject} currently has. */
    int countForSubject(String subject);

    /** Every subject with at least one active session right now. */
    Set<String> activeSubjects();

    /** Session TTL, in seconds — the sliding window each successful {@link #resolveSubject} and
     *  fresh {@link #create} extends. */
    long maxAgeSeconds();
}
