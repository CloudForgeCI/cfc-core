package com.cloudforge.core.manager.auth;

import java.util.List;

/**
 * CloudForge Manager's Users-page CRUD, abstracted over whichever directory of accounts is
 * currently authoritative — the local H2/Postgres DB, or an AWS Cognito User Pool. Replaces a
 * single persisted boolean flag ({@code AuthBackendStore.Config#cognitoEnabled()}) that used to be
 * branched on individually inside every one of {@code AuthService}'s five user-management methods
 * ({@code listUsers}/{@code createLocalUser}/{@code updateUser}/{@code revokeSessions}/{@code
 * deleteUser}) — {@code AuthService} now resolves one {@code AuthBackend} implementation per call
 * and delegates through it uniformly.
 *
 * <p><b>Deliberately scoped to exactly this — Users-page CRUD — and nothing else.</b> Caller/session
 * resolution during normal request handling ({@code AuthService.resolveCaller}/{@code
 * ensureOidcUser}) branches only on {@code authMode()} (the *sign-in* mechanism: none/alb-oidc/
 * application-oidc) and always resolves through the local {@code manager_user} table regardless of
 * which {@code AuthBackend} is active for account CRUD — a Cognito-backend-enabled account still
 * signs in via whatever OIDC flow {@code authMode} configures, gets upserted into the local table
 * on each successful principal, and its {@code Caller}/role come from that local row. Expanding
 * this interface to also own caller/session resolution would be an unrelated behavior change no
 * one asked for; if that ever needs to change, it's a deliberate, separate decision, not a
 * consequence of this interface's existence.</p>
 *
 * <p>Implementations: {@code LocalH2AuthBackend} (wraps {@code UserStore}+{@code SessionManager})
 * and {@code CognitoAuthBackend} (wraps {@code CognitoUserManagementService}), both in {@code
 * cloudforge-manager} — this interface and its DTOs ({@link AuthAccount}, {@link
 * AuthAccountRequest}, {@link AuthAccountUpdate}) live in {@code cloudforge-core} because they're
 * plain contracts with no Spring/persistence dependency, following the same split {@code
 * ApplicationSpec}/{@code DatabaseSpec} already establish for this module.</p>
 */
public interface AuthBackend {

    List<AuthAccount> listAccounts();

    AuthAccount createAccount(AuthAccountRequest request);

    AuthAccount updateAccount(String accountId, AuthAccountUpdate update);

    /** Invalidates every active session/token for this account without deleting it — the local
     *  equivalent of Cognito's {@code AdminUserGlobalSignOut}. Returns the (unchanged) account so
     *  callers can render an up-to-date view without a second lookup. */
    AuthAccount revokeSessions(String accountId);

    void deleteAccount(String accountId);

    /**
     * One-time setup a backend needs before it can accept migrated-in accounts (e.g. Cognito's IAM
     * role groups must exist before a migration starts adding users to them). Called once per
     * migration run, before the first {@link #createAccount}. No-op by default — {@code
     * LocalH2AuthBackend} has nothing to prepare.
     */
    default void prepareForIncomingMigration() {
    }
}
