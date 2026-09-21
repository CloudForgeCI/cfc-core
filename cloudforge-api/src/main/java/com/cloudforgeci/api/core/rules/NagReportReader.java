package com.cloudforgeci.api.core.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads cdk-nag's own {@code <PackName>-<StackName>-NagReport.json} files back off disk after
 * {@code app.synth()} completes, implementing {@code ComplianceMode.ENFORCE}'s "blocks CDK
 * synthesis" behavior. {@code app.synth()} itself never throws for a cdk-nag
 * violation regardless of severity, so a caller has to inspect findings explicitly; this is
 * that inspection point.
 *
 * <p>Deliberately file-based, not an {@code INagLogger} callback. Every {@code NagPack} in {@link
 * SecurityRules#mapFrameworkToNagPack} already sets {@code reports(true)}, which enables cdk-nag's
 * own built-in report logger (writing these exact files) with no extra registration needed.
 * Registering an <em>additional</em> {@code INagLogger} alongside that built-in one triggers a
 * jsii/cdk-nag runtime bug (a {@link StackOverflowError} from reentrant kernel calls, independent
 * of what the extra logger's callbacks do), so reading the generated report after synthesis has
 * already finished is both simpler and the only path that avoids it.</p>
 */
public final class NagReportReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NagReportReader() {
    }

    /** One row from a {@code NagReport}'s {@code lines[]} array. */
    public record ComplianceFinding(String ruleId, String level, String resourceId, String message) {
        /** One bullet-list line, e.g. {@code "AwsSolutions-S1 (LogBucket): server access logging
         *  is not enabled"} -- shared by the blocking exception's own message and the advisory
         *  stack Output, so both read identically. */
        public String describe() {
            String subject = resourceId == null || resourceId.isBlank() ? ruleId : ruleId + " (" + resourceId + ")";
            return subject + ": " + message;
        }
    }

    /**
     * Every {@code Non-Compliant}, {@code Error}-level line across all {@code *-NagReport.json}
     * files cdk-nag wrote for {@code stackName} into {@code assemblyDirectory} -- one file per
     * applied framework/pack, hence scanning the whole directory rather than guessing a single
     * exact filename. Compliant, suppressed, and warning-level lines are intentionally excluded --
     * only what {@code ComplianceMode.ENFORCE} actually needs to block on.
     *
     * <p>Best-effort: a missing or unparseable report file is treated as "no findings from that
     * file" rather than failing synthesis over a reporting problem — the same fail-soft posture
     * the rest of this codebase's optional/best-effort reads already take.</p>
     */
    public static List<ComplianceFinding> readErrors(Path assemblyDirectory, String stackName) {
        return readReports(assemblyDirectory, stackName, true);
    }

    /**
     * Every line across all {@code *-NagReport.json} files cdk-nag wrote for {@code stackName},
     * regardless of compliance/level -- compliant, suppressed, and warning-level lines included.
     * Unlike {@link #readErrors} (which exists to answer "must synthesis be blocked"), this exists
     * to answer "what does a full compliance report for this stack look like", for a caller (an
     * on-demand advisory check, not a real deploy) that wants to show the user everything cdk-nag
     * has to say, not just what would fail an enforce-mode deploy.
     */
    public static List<ComplianceFinding> readAll(Path assemblyDirectory, String stackName) {
        return readReports(assemblyDirectory, stackName, false);
    }

    private static List<ComplianceFinding> readReports(Path assemblyDirectory, String stackName, boolean errorsOnly) {
        List<ComplianceFinding> findings = new ArrayList<>();
        String suffix = "-" + stackName + "-NagReport.json";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(assemblyDirectory, "*" + suffix)) {
            for (Path reportFile : stream) {
                findings.addAll(readOneFile(reportFile, errorsOnly));
            }
        } catch (IOException ignored) {
            // Best-effort; see javadoc.
        }
        return findings;
    }

    private static List<ComplianceFinding> readOneFile(Path reportFile, boolean errorsOnly) {
        List<ComplianceFinding> findings = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(reportFile.toFile());
            JsonNode lines = root.path("lines");
            for (JsonNode line : lines) {
                String compliance = line.path("compliance").asText("");
                String level = line.path("ruleLevel").asText("");
                if (errorsOnly && (!isNonCompliant(compliance) || !"error".equals(level.toLowerCase(Locale.ROOT)))) {
                    continue;
                }
                findings.add(new ComplianceFinding(
                    line.path("ruleId").asText(null),
                    level.isBlank() ? compliance.toUpperCase(Locale.ROOT) : level.toUpperCase(Locale.ROOT),
                    line.path("resourceId").asText(null),
                    line.path("ruleInfo").asText(null)));
            }
        } catch (IOException ignored) {
            // Best-effort; see readErrors.
        }
        return findings;
    }

    /** cdk-nag's own {@code compliance} field is a human-readable string ({@code "Non-Compliant"}
     *  for a violation); matched case-insensitively and by substring so a minor wording
     *  change across cdk-nag versions degrades to "excluded" rather than a hard parse failure. */
    private static boolean isNonCompliant(String compliance) {
        return compliance.toLowerCase(Locale.ROOT).contains("non-compliant");
    }
}
