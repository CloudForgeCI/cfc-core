package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.rules.ComplianceMatrix.SecurityControl;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the set a rule class returns from {@code FrameworkRules.claimedControls()}. That interface
 * lives in a module that cannot see {@link SecurityControl}, so it uses control names; building the
 * set from enum constants makes a renamed or removed control a compile error.
 */
public final class ClaimedControls {

    private ClaimedControls() {
    }

    /** The names of the given controls. */
    public static Set<String> of(SecurityControl... controls) {
        return Arrays.stream(controls).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
