package com.cloudforgeci.api.core.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local side channel for framework-rule ({@link com.cloudforge.core.interfaces.FrameworkRules#install})
 * ADVISORY-mode findings -- see {@link NagReportReader} for cdk-nag's own equivalent, file-based
 * mechanism.
 *
 * <p>Every {@code FrameworkRules} implementation's {@code Node.addValidation} callback returns an
 * empty list in {@code ComplianceMode.ADVISORY} specifically so {@code app.synth()} doesn't throw
 * -- that's the whole point of advisory mode not blocking synthesis. But an empty return also
 * means the violation detail (until now, only ever written to a {@code Logger.warning} call) never
 * reaches any caller as data. A caller that wants to actually show those findings to a user (an
 * on-demand advisory compliance check, not a real deploy) has no other way to see them, since the
 * {@code Node.addValidation} return value is the only channel back out of the callback and it
 * has to stay empty.</p>
 *
 * <p>A plain {@link ThreadLocal} is safe here specifically because {@code CloudForgeSynthesizer
 * #synthesize} already serializes every synth call behind a single static lock (see that class's
 * own javadoc for why) -- no two synth calls ever run concurrently in the same JVM, regardless of
 * which thread invokes them, so there's no risk of one synth's findings leaking into another's.</p>
 */
public final class ComplianceFindingsCollector {

    private static final ThreadLocal<List<NagReportReader.ComplianceFinding>> CURRENT = new ThreadLocal<>();

    private ComplianceFindingsCollector() {
    }

    /** Call before {@code app.synth()} to start collecting on this thread. A synth run that never
     *  calls this (every normal deploy today) leaves {@link #record} a no-op, so this is fully
     *  opt-in and changes nothing for existing callers. */
    public static void start() {
        CURRENT.set(new ArrayList<>());
    }

    /** Called from a {@code FrameworkRules} implementation's ADVISORY branch, alongside (not
     *  instead of) its existing {@code LOG.warning} calls. */
    public static void record(List<ComplianceRule> failedRules) {
        List<NagReportReader.ComplianceFinding> list = CURRENT.get();
        if (list == null || failedRules == null) {
            return;
        }
        for (ComplianceRule rule : failedRules) {
            list.add(new NagReportReader.ComplianceFinding(
                rule.ruleId(),
                "WARNING",
                null,
                rule.description() + rule.errorMessage().map(msg -> " - " + msg).orElse("")));
        }
    }

    /** Stops collecting and returns everything gathered since the matching {@link #start()} --
     *  always pair this with a {@code finally} block, or a later synth on the same thread would
     *  otherwise keep appending to a stale, already-returned list indefinitely. */
    public static List<NagReportReader.ComplianceFinding> drain() {
        List<NagReportReader.ComplianceFinding> list = CURRENT.get();
        CURRENT.remove();
        return list == null ? List.of() : List.copyOf(list);
    }
}
