package com.cloudforge.core.manager.auth;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * CloudForge Manager's Users-page CRUD, abstracted over whichever directory of accounts is
 * currently authoritative — the local H2/Postgres DB, or an AWS Cognito User Pool. {@code
 * AuthService} resolves one {@code AuthBackend} implementation per call (based on the persisted
 * {@code AuthBackendStore.Config#cognitoEnabled()} flag) and delegates its user-management
 * methods ({@code listUsers}/{@code createLocalUser}/{@code updateUser}/{@code revokeSessions}/
 * {@code deleteUser}) through it.
 *
 * <p><b>Deliberately scoped to exactly this — Users-page CRUD — and nothing else.</b> Caller/session
 * resolution during normal request handling ({@code AuthService.resolveCaller}/{@code
 * ensureOidcUser}) branches only on {@code authMode()} (the *sign-in* mechanism: none/alb-oidc/
 * application-oidc) and always resolves through the local {@code manager_user} table regardless of
 * which {@code AuthBackend} is active for account CRUD — a Cognito-backend-enabled account still
 * signs in via whatever OIDC flow {@code authMode} configures, gets upserted into the local table
 * on each successful principal, and its {@code Caller}/role come from that local row. Moving
 * caller/session resolution into this interface would be a separate behavior change.</p>
 *
 * <p>Implementations: {@code LocalH2AuthBackend} (wraps {@code UserStore}+{@code SessionManager})
 * and {@code CognitoAuthBackend} (wraps {@code CognitoUserManagementService}), both in {@code
 * cloudforge-manager} — this interface and its DTOs ({@link AuthAccount}, {@link
 * AuthAccountRequest}, {@link AuthAccountUpdate}) live in {@code cloudforge-core} because they're
 * plain contracts with no Spring/persistence dependency, following the same split as {@code
 * ApplicationSpec}/{@code DatabaseSpec}.</p>
 */
public interface AuthBackend {

    List<AuthAccount> listAccounts();

    /**
     * Same as {@link #listAccounts()}, narrowed by two optional filters: {@code search} (matches
     * {@link AuthAccount#username()}/{@link AuthAccount#email()}/{@link
     * AuthAccount#displayName()}, case-insensitive substring) and {@code role} (exact match).
     * Both {@code null}/blank mean "unfiltered" — equivalent to {@link #listAccounts()}.
     *
     * <p>The default implementation filters in Java over {@link #listAccounts()} — correct for
     * any backend, but only moves the filtering server-side (out of the Angular Users page)
     * without reducing the underlying account-directory calls. {@code LocalH2AuthBackend}
     * overrides this with a database-level query. {@code CognitoAuthBackend} keeps this default
     * deliberately: Cognito's own {@code ListUsers} {@code Filter} syntax has no concept of role
     * (role lives in group membership, not a filterable user attribute), so even an override
     * there couldn't push the {@code role} filter down — only {@code search} could move, with no
     * reduction in Cognito API calls either, the same trade-off {@code
     * CloudFormationInventory#listInstances(String, String)}'s {@code search} parameter
     * documents for AWS's own {@code ListStacks}.</p>
     */
    default List<AuthAccount> listAccounts(String search, String role) {
        String searchLower = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        String roleFilter = role == null || role.isBlank() ? null : role;
        return listAccounts().stream()
            .filter(account -> roleFilter == null || roleFilter.equalsIgnoreCase(account.role()))
            .filter(account -> searchLower == null || matchesSearch(account, searchLower))
            .toList();
    }

    private static boolean matchesSearch(AuthAccount account, String searchLower) {
        return Stream.of(account.username(), account.email(), account.displayName())
            .filter(Objects::nonNull)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(value -> value.contains(searchLower));
    }

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

    /**
     * Finds one account by its {@link AuthAccount#id()}, or empty if this backend has no such
     * account. Exists so callers that only have an id — e.g. Access Control's policy-override
     * editor, given whatever id {@link #listAccounts()} last handed the frontend — can resolve a
     * username/role for display without requiring the account to also exist in Manager's local
     * user table. A Cognito-only account that has never signed in through application-oidc has
     * no local row, so a local-table lookup would report "user not found" for an account
     * {@link #listAccounts()} just returned.
     *
     * <p>Default implementation scans {@link #listAccounts()} — correct for any backend, just not
     * the cheapest possible lookup; override when a backend can look up a single account more
     * directly (see {@code CognitoAuthBackend}).
     */
    default Optional<AuthAccount> findAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return listAccounts().stream().filter(account -> accountId.equals(account.id())).findFirst();
    }
}
