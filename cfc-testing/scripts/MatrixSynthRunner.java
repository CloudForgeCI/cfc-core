import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforgeci.api.deploy.CloudForgeSynthesizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synthesizes every matrix row in-process and judges it against what the row expects.
 *
 * <pre>
 * java -cp "target/dependency/*" scripts/MatrixSynthRunner.java matrix.csv base-context.json results.tsv tier[,tier] [rowIdContains]
 * </pre>
 *
 * A PASS row only passes if SOC2 validation demonstrably ran and passed ("validation passed (N checks)").
 * A FAIL row only passes if synthesis is rejected with the expected rule id -- any other rejection
 * (exception, missing asset, unrelated rule) is reported as FAIL-WRONG-REASON, and a synthesis that succeeds
 * is reported as FINDING-RULE-DID-NOT-FIRE. Nothing here deploys; results feed the tracker.
 */
public class MatrixSynthRunner {
    static final ObjectMapper MAPPER = new ObjectMapper();
    // Matches every framework's own "<Name> validation passed (N checks)" message (Soc2Rules,
    // HipaaRules, PciDssRules, GdprRules, FedRampRules each log a different <Name> prefix).
    static final Pattern CHECKS = Pattern.compile("validation passed \\((\\d+) checks\\)");
    static final List<String> VERIFY_LINES = new ArrayList<>();
    // A rule id: an uppercase-led token, then one or more "-segment" groups where a segment can
    // hold letters/digits/dots/parens (covers "SOC2-CC6.7-SSL", "HIPAA-164.312(a)(2)(i)-Auth",
    // "PCI-DSS-Req-2.2-IMDSv2", "GDPR-IMDSV2", and bare always-load ids like "MESSAGING-ENCRYPTION").
    static final Pattern RULE = Pattern.compile("\\b([A-Z][A-Za-z0-9]*(?:-[A-Za-z0-9.()]+)+)\\b");

    public static void main(String[] args) throws Exception {
        Path matrix = Path.of(args[0]);
        ObjectNode base = (ObjectNode) MAPPER.readTree(Files.readString(Path.of(args[1])));
        Path results = Path.of(args[2]);
        Set<String> tiers = Set.of(args[3].split(","));
        String contains = args.length > 4 ? args[4] : "";

        Set<String> configKeys = new HashSet<>();
        for (Field f : DeploymentConfig.class.getFields()) {
            configKeys.add(f.getName());
        }

        CapturingHandler capture = new CapturingHandler();
        Logger.getLogger("").addHandler(capture);
        Logger.getLogger("").setLevel(Level.INFO);

        List<String[]> rows = readCsv(matrix);
        String[] header = rows.get(0);
        List<String> out = new ArrayList<>();
        out.add("rowId\tresult\tchecks\tdetail");
        for (String[] r : rows.subList(1, rows.size())) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) {
                row.put(header[i], i < r.length ? r[i] : "");
            }
            if (!tiers.contains(row.get("tier")) || !row.get("rowId").contains(contains)) {
                continue;
            }
            String[] verdict = run(row, base, configKeys, capture);
            out.add(row.get("rowId") + "\t" + String.join("\t", verdict));
            System.out.println(row.get("rowId") + " -> " + verdict[0] + " " + verdict[1] + " " + verdict[2]);
        }
        Files.write(results, out);
        String verifyPath = System.getProperty("verify");
        if (verifyPath != null) {
            List<String> lines = new ArrayList<>();
            lines.add("rowId\trule\tstatus\tdetail");
            lines.addAll(VERIFY_LINES);
            Files.write(Path.of(verifyPath), lines);
        }
    }

    static String[] run(Map<String, String> row, ObjectNode base, Set<String> configKeys, CapturingHandler capture) {
        try {
            ObjectNode context = base.deepCopy();
            String stackName = "M" + Integer.toHexString(row.get("rowId").hashCode());
            context.put("stackName", stackName);
            context.put("applicationId", row.get("app"));
            context.put("applicationName", row.get("app"));
            context.put("runtime", row.get("runtime"));
            context.put("securityProfile", row.get("profile"));
            context.put("cognitoDomainPrefix", "m" + Integer.toHexString(row.get("rowId").hashCode()));
            if ("EC2".equals(row.get("runtime"))) {
                context.remove("cpu");
                context.remove("memory");
                context.put("instanceType", "t3.small");
            }
            JsonNode overrides = MAPPER.readTree(row.get("overrides"));
            Set<String> unknown = new TreeSet<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = overrides.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                if (!configKeys.contains(e.getKey())) {
                    unknown.add(e.getKey());
                }
                context.set(e.getKey(), e.getValue());
            }
            if (!unknown.isEmpty()) {
                return new String[] {"FINDING-UNKNOWN-CONFIG-KEY", "", "not a DeploymentConfig field: " + unknown};
            }

            capture.reset();
            String thrown = null;
            CloudForgeSynthesizer.Result synthesized = null;
            try {
                DeploymentConfig config = DeploymentConfig.fromJson(context.toString());
                synthesized = CloudForgeSynthesizer.synthesize(config, Files.createTempDirectory("matrix-synth"));
            } catch (Throwable t) {
                thrown = rootMessage(t);
            }
            String[] verdict = judge(row, thrown, capture.text());
            if (synthesized != null && "PASS".equals(row.get("expected")) && System.getProperty("verify") != null) {
                JsonNode template = MAPPER.readTree(synthesized.templateFile().toFile());
                if (System.getProperty("dump") != null) {
                    Files.copy(synthesized.templateFile(), Path.of(System.getProperty("dump"), row.get("rowId") + ".json"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                for (TemplateVerifier.Check c : new TemplateVerifier(template, row.get("runtime"),
                        row.get("authMode"), row.get("profile"),
                        context.path("maxInstanceCapacity").asInt(1) <= 1,
                        context.path("backupCrossRegionVaultArn").asText("").length() > 0).verify()) {
                    VERIFY_LINES.add(row.get("rowId") + "\t" + c.ruleId() + "\t" + c.status() + "\t" + c.detail());
                }
            }
            return verdict;
        } catch (Throwable t) {
            return new String[] {"ERROR", "", rootMessage(t)};
        }
    }

    static String[] judge(Map<String, String> row, String thrown, String log) {
        String expected = row.get("expected");
        String expectedRule = row.get("expectedRule");
        Matcher passed = CHECKS.matcher(log);
        String checks = passed.find() ? passed.group(1) : "";
        Set<String> firedRules = new TreeSet<>();
        Matcher rules = RULE.matcher(thrown == null ? "" : thrown);
        while (rules.find()) {
            firedRules.add(rules.group(1));
        }
        // Failures logged (not thrown) while running in ADVISORY complianceMode -- violations that
        // didn't block synthesis, distinct from an ADVISORY-tier row's recommendation.
        Matcher logged = Pattern.compile("- (" + RULE.pattern() + "):").matcher(log);
        while (logged.find()) {
            firedRules.add(logged.group(1));
        }

        // ADVISORY-tier findings: ComplianceRule.advisory(...), always logged with this marker
        // regardless of complianceMode (see each Rules.java's install()). passed=true, so these
        // never appear in firedRules above.
        Set<String> advisoryFindings = new TreeSet<>();
        Matcher advisories = Pattern.compile("\\[ADVISORY] (" + RULE.pattern() + "):").matcher(log);
        while (advisories.find()) {
            advisoryFindings.add(advisories.group(1));
        }

        switch (expected) {
            case "PASS":
                if (thrown != null) {
                    return new String[] {"FAIL", "", "expected PASS but synthesis threw: " + abbreviate(thrown)};
                }
                if (checks.isEmpty()) {
                    return new String[] {"FAIL-NOT-VALIDATED", "", "synthesis succeeded but validation never ran"};
                }
                return firedRules.isEmpty()
                    ? new String[] {"PASS", checks, ""}
                    : new String[] {"FAIL", checks, "advisory-mode findings on a PASS row: " + firedRules};
            case "SKIP":
                return thrown == null && log.contains("typically apply to PRODUCTION and STAGING")
                    ? new String[] {"PASS", "", "validation skipped for DEV as designed"}
                    : new String[] {"FAIL", "", "expected DEV to skip validation: " + abbreviate(thrown)};
            case "ADVISORY":
                // A finding is expected but must not block: synthesis must succeed, and the specific
                // rule must show up in the advisory log, not as a thrown (blocking) failure.
                if (thrown != null) {
                    return new String[] {"FAIL", "", "expected ADVISORY but synthesis threw: " + abbreviate(thrown)};
                }
                if (checks.isEmpty()) {
                    return new String[] {"FAIL-NOT-VALIDATED", "", "synthesis succeeded but validation never ran"};
                }
                return advisoryFindings.contains(expectedRule)
                    ? new String[] {"PASS", checks, "advisory finding present: " + expectedRule}
                    : new String[] {"FINDING-RULE-DID-NOT-FIRE", checks,
                        "synthesis succeeded; control forced on/off or rule unreachable via config: " + expectedRule};
            default:
                if (thrown == null) {
                    return new String[] {"FINDING-RULE-DID-NOT-FIRE", checks,
                        "synthesis succeeded; control forced on or rule unreachable via config: " + expectedRule};
                }
                if (firedRules.contains(expectedRule)) {
                    return new String[] {"PASS", "", "rejected by " + expectedRule + " (all: " + firedRules + ")"};
                }
                return new String[] {"FAIL-WRONG-REASON", "",
                    "expected " + expectedRule + " but got " + firedRules + " / " + abbreviate(thrown)};
        }
    }

    static String rootMessage(Throwable t) {
        Throwable c = t;
        StringBuilder all = new StringBuilder();
        while (c != null) {
            if (c.getMessage() != null) {
                all.append(c.getMessage()).append(" | ");
            }
            c = c.getCause();
        }
        return all.toString().replace('\n', ' ').replace('\t', ' ');
    }

    static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    /** Minimal RFC-4180 CSV reader (quoted fields, doubled quotes, no embedded newlines needed here). */
    static List<String[]> readCsv(Path path) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(Files.readString(path)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = new ArrayList<>();
                StringBuilder cur = new StringBuilder();
                boolean quoted = false;
                for (int i = 0; i < line.length(); i++) {
                    char ch = line.charAt(i);
                    if (quoted) {
                        if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            cur.append('"');
                            i++;
                        } else if (ch == '"') {
                            quoted = false;
                        } else {
                            cur.append(ch);
                        }
                    } else if (ch == '"') {
                        quoted = true;
                    } else if (ch == ',') {
                        fields.add(cur.toString());
                        cur.setLength(0);
                    } else {
                        cur.append(ch);
                    }
                }
                fields.add(cur.toString());
                rows.add(fields.toArray(new String[0]));
            }
        }
        return rows;
    }

    /** Collects everything logged during one row's synthesis. */
    static class CapturingHandler extends Handler {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public synchronized void publish(LogRecord record) {
            if (record.getMessage() != null) {
                buffer.append(record.getMessage()).append('\n');
            }
        }

        synchronized void reset() {
            buffer.setLength(0);
        }

        synchronized String text() {
            return buffer.toString();
        }

        @Override
        public void flush() { }

        @Override
        public void close() { }
    }
}
