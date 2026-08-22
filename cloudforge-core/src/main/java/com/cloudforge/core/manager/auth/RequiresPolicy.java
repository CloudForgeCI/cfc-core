package com.cloudforge.core.manager.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a CloudForge Manager REST controller method as requiring a resolved caller who is
 * permitted the named policy. {@link #value()} is a plain policy-ID string matching one of the
 * constants in {@code com.cloudforgeci.manager.auth.ManagerPolicyCatalog} (e.g.
 * {@code "deploy:create"}) — deliberately a {@code String}, not an enum, so this annotation (which
 * lives in {@code cloudforge-core} and knows nothing about Manager's policy catalog) never needs
 * to change when a new policy is added there.
 *
 * <p>Enforced by {@code AuthorizationInterceptor} against
 * {@code com.cloudforgeci.manager.web.AccessGuard#requireCaller} followed by
 * {@code com.cloudforgeci.manager.auth.AuthService#requirePermission(Caller, String)}. See
 * {@link RequiresAccess} for the "every method must carry exactly one of these four annotations"
 * contract.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPolicy {
    String value();
}
