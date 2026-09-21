package com.cloudforge.core.manager.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a CloudForge Manager REST controller method as requiring a resolved caller who is
 * permitted at least one of the named policies. {@link RequiresPolicy} covers the common case of
 * a single required policy; this covers the rarer case of one endpoint legitimately serving two
 * distinct callers with two distinct policies — e.g. the Deploy wizard's "redeploy with changes"
 * prefill (already gated by {@code deploy:create} to submit the redeploy) and a plain
 * read-only viewer of the same lookup data (gated by its own dedicated policy instead, so it
 * doesn't also need deploy rights just to look). {@link #value()} is a plain array of policy-ID
 * strings matching constants in {@code com.cloudforgeci.manager.auth.ManagerPolicyCatalog} — see
 * {@link RequiresPolicy}'s javadoc for why these are strings, not an enum.
 *
 * <p>Enforced by {@code AuthorizationInterceptor} against
 * {@code com.cloudforgeci.manager.web.AccessGuard#requireCaller} followed by
 * {@code com.cloudforgeci.manager.auth.AuthService#hasAnyAuthority(Caller, String...)}, granting
 * access if any named policy matches. See {@link RequiresAccess} for the "every method must carry
 * exactly one of these annotations" contract.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresAnyPolicy {
    String[] value();
}
