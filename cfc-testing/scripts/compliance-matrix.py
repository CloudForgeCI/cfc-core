#!/usr/bin/env python3
"""Generates the compliance deployment matrix (one row per variation to verify) for a framework.

    python3 scripts/compliance-matrix.py soc2

Reads compliance-matrix/app-inventory.tsv (produced by scripts/AppInventory.java) and writes
compliance-matrix/<framework>-matrix.csv. Existing results (status, checks, ...) are preserved when a row
with the same rowId is regenerated, so the file doubles as the progress tracker for the runner.
"""
import csv
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MATRIX_DIR = ROOT / "compliance-matrix"

COLUMNS = ["rowId", "framework", "tier", "app", "runtime", "profile", "authMode", "expected", "expectedRule",
           "overrides", "status", "checks", "deployStatus", "verified", "verifiedAt", "notes"]

# Apps excluded from compliance matrices: the Manager is the console, not a compliance subject, and the two
# Mattermost editions are one application (the free edition deploys without a license).
EXCLUDED_APPS = {"cloudforge-manager", "mattermost-enterprise"}

# What a SOC2-compliant deployment of any app needs on top of the app's own defaults.
SOC2_BASELINE = {
    "complianceFrameworks": "SOC2",
    "auditManagerEnabled": True,
    "awsConfigEnabled": True,
    "enableSsl": True,
    "wafEnabled": True,
    "networkMode": "private-with-nat",
    "logRetentionDays": 365,
}

# One override that turns a single SOC2 control off, and the rule that must then fail.
# "runtime" restricts a row to one runtime. The runner records rows whose rule never fires as findings.
# Every key here is read before any ComplianceMatrix-required check in ProductionSecurityProfileConfiguration
# (or isn't matrix-gated at all), so the override actually reaches the rule below. See SOC2_STRUCTURAL for
# the controls SOC2 marks REQUIRED, where PRODUCTION hardcodes compliance and the override has no effect.
SOC2_NEGATIVES = [
    ("enableSsl", False, "SOC2-CC6.7-SSL", None),
    ("wafEnabled", False, "SOC2-CC6.6-WAF", None),
    ("networkMode", "public-no-nat", "SOC2-C1.2-Network", None),
    ("logRetentionDays", 30, "SOC2-CC7.2-LogRetention", None),
    ("authMode", "none", "SOC2-CC6.2-Auth", None),
]

# Controls SOC2 marks REQUIRED in ComplianceMatrix: ProductionSecurityProfileConfiguration hardcodes
# these compliant once complianceFrameworks includes SOC2, ahead of any deployment-context override.
# ebsEncryptionEnabled/efsEncryptionAtRestEnabled/s3EncryptionEnabled/imdsv2Required now have real
# override fields (added for the ADVISORY-level frameworks that need them -- see PCI-DSS/GDPR's own
# comments below), but SOC2 marks all four REQUIRED, so the override still can't move them here.
# multiAzEnforced has no DeploymentConfig field at all -- the runner reports it as an unknown-config-key
# finding rather than judging PASS/FAIL. Each row below proves the point: the override is set, and
# synthesis is still expected to PASS because SOC2 does not let the control be turned off.
SOC2_STRUCTURAL = [
    ("cloudTrailEnabled", False, "SOC2-CC7.2-CloudTrail", None),
    ("enableFlowlogs", False, "SOC2-CC7.2-FlowLogs", None),
    ("awsConfigEnabled", False, "SOC2-CC7.2-Config", None),
    ("ebsEncryptionEnabled", False, "SOC2-C1.1-EBS", None),
    ("efsEncryptionAtRestEnabled", False, "SOC2-C1.1-EFS", None),
    ("efsEncryptionInTransitEnabled", False, "SOC2-CC6.7-EFS", None),
    ("s3EncryptionEnabled", False, "SOC2-C1.1-S3", None),
    ("cloudWatchLogsKmsEncryptionEnabled", False, "SOC2-CC6.1-LogEncryption", None),
    ("cloudTrailInsightsEnabled", False, "SOC2-CC7.2-CloudTrailInsights", None),
    ("route53QueryLoggingEnabled", False, "SOC2-CC7.2-Route53QueryLogging", None),
    ("s3ObjectLockEnabled", False, "SOC2-CC7.2-AuditLogImmutability", None),
    ("imdsv2Required", False, "SOC2-CC6.6-IMDSv2", "EC2"),
    ("multiAzEnforced", False, "SOC2-A1.2-MultiAZ", None),
    ("enableAutoScaling", False, "SOC2-A1.2-AutoScaling", None),
    ("automatedBackupEnabled", False, "SOC2-A1.3-Backup", None),
    ("crossRegionBackupEnabled", False, "SOC2-A1.3-CrossRegion", None),
]

# SNS_KMS_ENCRYPTION, SECURITY_MONITORING and THREAT_DETECTION are ADVISORY for SOC2
# (ComplianceMatrix); each now reports via ComplianceRule.advisory() when off instead of silently
# passing -- MessagingSecurityRules for SNS-KMS, Soc2Rules#validateSystemMonitoring for the other two.
SOC2_ADVISORY = [
    ("snsKmsEncryptionEnabled", False, "MESSAGING-ENCRYPTION", None),
    ("securityMonitoringEnabled", False, "SOC2-CC7.2-Monitoring", None),
    ("guardDutyEnabled", False, "SOC2-CC7.2-GuardDuty", None),
]

RUNTIMES = ["FARGATE", "EC2"]
PROFILES = ["STAGING", "PRODUCTION"]
NEGATIVE_SUBJECT = "jenkins"


def load_inventory():
    with open(MATRIX_DIR / "app-inventory.tsv", newline="") as f:
        return [r for r in csv.DictReader(f, delimiter="\t") if r["applicationId"] not in EXCLUDED_APPS]


def soc2_rows():
    apps = load_inventory()
    rows = []
    for app in apps:
        modes = app["authModes"].split(",")
        auth = "application-oidc" if "application-oidc" in modes else ("alb-oidc" if "alb-oidc" in modes else None)
        for runtime in RUNTIMES:
            for profile in PROFILES:
                overrides = dict(SOC2_BASELINE)
                if app["database"] in ("REQUIRED", "OPTIONAL"):
                    overrides["provisionDatabase"] = True
                if auth:
                    overrides["authMode"] = auth
                    expected, rule = "PASS", ""
                else:
                    # No compliant auth path exists for this app, so SOC2 CC6.2 has to reject it.
                    overrides["authMode"] = "none"
                    expected, rule = "FAIL", "SOC2-CC6.2-Auth"
                    if profile != "PRODUCTION" or runtime != "FARGATE":
                        continue  # one cheap synthesis-only failure row per app is enough
                rows.append(row("soc2", "app", app["applicationId"], runtime, profile, overrides, expected, rule,
                                SOC2_BASELINE))

    # DEV is out of scope for SOC2 validation by design; one row per runtime proves it is skipped.
    for runtime in RUNTIMES:
        overrides = dict(SOC2_BASELINE)
        overrides["authMode"] = "application-oidc"
        rows.append(row("soc2", "dev-skip", NEGATIVE_SUBJECT, runtime, "DEV", overrides, "SKIP", "", SOC2_BASELINE))

    # Rule-by-rule negatives on the subject app: turn one control off, the matching rule must fail.
    for key, value, rule, only_runtime in SOC2_NEGATIVES:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(SOC2_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("soc2", "negative", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "FAIL", rule,
                            SOC2_BASELINE))

    # Same override, opposite point: the control is REQUIRED for SOC2, so the row proves the override
    # cannot weaken it -- synthesis passes with the control still compliant, not the rule firing.
    for key, value, rule, only_runtime in SOC2_STRUCTURAL:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(SOC2_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("soc2", "structural", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "PASS", rule,
                            SOC2_BASELINE))

    # ADVISORY: control is off and the framework doesn't require it -- expect a non-blocking
    # recommendation, not a hard FAIL.
    for key, value, rule, only_runtime in SOC2_ADVISORY:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(SOC2_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("soc2", "advisory", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "ADVISORY", rule,
                            SOC2_BASELINE))
    return rows


# ---------------------------------------------------------------------------
# HIPAA
# ---------------------------------------------------------------------------
# Baseline: awsConfigEnabled/enableSsl/wafEnabled/networkMode/logRetentionDays mirror SOC2's
# infra baseline; logRetentionDays=2190 is HIPAA's own floor (SS164.316(b)(2)(i), 6 years -- see
# HipaaRules#isRetentionSufficient, which only accepts RetentionDays.SIX_YEARS or longer).
# cognitoAutoProvision/cognitoMfaEnabled are required here (not just in negatives) because
# HipaaRules#validateAuthenticationControls checks live cognitoMfaEnabled for every ALB/APPLICATION
# OIDC app row, and the shared base-context.json this matrix is layered on defaults it to false.
HIPAA_BASELINE = {
    "complianceFrameworks": "HIPAA",
    "auditManagerEnabled": True,  # master gate: SecurityRules.install() skips ALL FrameworkRules without it
    "awsConfigEnabled": True,
    "enableSsl": True,
    "wafEnabled": True,
    "networkMode": "private-with-nat",
    "logRetentionDays": 2190,
    "cognitoAutoProvision": True,
    "cognitoMfaEnabled": True,
    "guardDutyEnabled": True,  # THREAT_DETECTION is REQUIRED for HIPAA
    "macieEnabled": True,  # AdvancedMonitoringRules checks this unconditionally for GDPR/HIPAA
    "macieAutomatedDiscovery": True,  # required once macieEnabled is true, same GDPR/HIPAA check
}

# Every key here was traced to a real DeploymentConfig field AND confirmed to be read before (or
# without) any ComplianceMatrix-required hardcode in ProductionSecurityProfileConfiguration -- see
# the report for the much longer list of SOC2_NEGATIVES-style keys that were excluded because
# PRODUCTION hardcodes the control to compliant once the framework marks it REQUIRED (or, for
# ebsEncryptionEnabled/efsEncryptionAtRestEnabled/s3EncryptionEnabled/imdsv2Required/
# multiAzEnforced/enableAutoScaling, because no DeploymentConfig field of that name exists at all).
HIPAA_NEGATIVES = [
    ("enableSsl", False, "HIPAA-164.312(e)(2)(i)-SSL", None),
    ("networkMode", "public-no-nat", "HIPAA-164.312(e)(1)-Network", None),
    ("authMode", "none", "HIPAA-164.312(a)(2)(i)-Auth", None),
    ("logRetentionDays", 365, "HIPAA-164.316(b)(2)(i)-Retention", None),
    ("cognitoMfaEnabled", False, "HIPAA-164.312(d)-MFA", None),
]

# Controls HIPAA checks (HipaaRules.java) that ComplianceMatrix marks REQUIRED for HIPAA, so
# PRODUCTION hardcodes them compliant and the override cannot weaken them -- same reasoning as
# SOC2_STRUCTURAL. ebsEncryptionEnabled/efsEncryptionAtRestEnabled/s3EncryptionEnabled/imdsv2Required
# now have real override fields, but HIPAA marks all four REQUIRED so the override is still moot here.
# multiAzEnforced has no matching DeploymentConfig field at all, same as SOC2's.
# rdsDatabaseMultiAzEnabled/rdsDeletionProtectionEnabled are also fixed now, but HipaaRules only
# checks them when a database was provisioned, and the jenkins subject app never provisions one --
# there's no row here that would exercise either rule regardless of the getter.
HIPAA_STRUCTURAL = [
    ("securityMonitoringEnabled", False, "HIPAA-164.308(a)(1)(ii)(D)-Monitoring", None),
    ("guardDutyEnabled", False, "HIPAA-164.308(a)(1)(ii)(D)-GuardDuty", None),
    ("automatedBackupEnabled", False, "HIPAA-164.310(d)(2)(iii)-Backup", None),
    ("crossRegionBackupEnabled", False, "HIPAA-164.310(d)(2)(iii)-CrossRegion", None),
    ("cloudTrailEnabled", False, "HIPAA-164.312(b)-CloudTrail", None),
    ("enableFlowlogs", False, "HIPAA-164.312(b)-FlowLogs", None),
    ("albAccessLogging", False, "HIPAA-164.312(b)-ALB", None),
    ("efsEncryptionInTransitEnabled", False, "HIPAA-164.312(e)(2)(ii)-EFS", None),
    ("ebsEncryptionEnabled", False, "HIPAA-164.312(a)(2)(iv)-EncryptionAtRest", None),
    ("efsEncryptionAtRestEnabled", False, "HIPAA-164.312(a)(2)(iv)-EncryptionAtRest", None),
    ("s3EncryptionEnabled", False, "HIPAA-164.312(a)(2)(iv)-EncryptionAtRest", None),
    ("cloudWatchLogsKmsEncryptionEnabled", False, "HIPAA-164.312(a)(2)(iv)-LogEncryption", None),
    ("s3ObjectLockEnabled", False, "HIPAA-164.312(c)(1)-AuditLogImmutability", None),
    ("imdsv2Required", False, "HIPAA-164.312(a)(1)-IMDSv2", "EC2"),
    ("multiAzEnforced", False, "HIPAA-164.308(a)(7)(ii)(B)-HighAvailability", None),
    ("enableAutoScaling", False, "HIPAA-164.308(a)(7)(ii)(B)-HighAvailability", None),
    ("awsConfigEnabled", False, "HIPAA-164.308(a)(8)-ChangeManagement", None),
    ("snsKmsEncryptionEnabled", False, "MESSAGING-ENCRYPTION", None),
]


def hipaa_rows():
    apps = load_inventory()
    rows = []
    for app in apps:
        modes = app["authModes"].split(",")
        auth = "application-oidc" if "application-oidc" in modes else ("alb-oidc" if "alb-oidc" in modes else None)
        for runtime in RUNTIMES:
            for profile in PROFILES:
                overrides = dict(HIPAA_BASELINE)
                if app["database"] in ("REQUIRED", "OPTIONAL"):
                    overrides["provisionDatabase"] = True
                if auth:
                    overrides["authMode"] = auth
                    expected, rule = "PASS", ""
                else:
                    overrides["authMode"] = "none"
                    expected, rule = "FAIL", "HIPAA-164.312(a)(2)(i)-Auth"
                    if profile != "PRODUCTION" or runtime != "FARGATE":
                        continue
                rows.append(row("hipaa", "app", app["applicationId"], runtime, profile, overrides, expected, rule,
                                HIPAA_BASELINE))

    for runtime in RUNTIMES:
        overrides = dict(HIPAA_BASELINE)
        overrides["authMode"] = "application-oidc"
        rows.append(row("hipaa", "dev-skip", NEGATIVE_SUBJECT, runtime, "DEV", overrides, "SKIP", "",
                        HIPAA_BASELINE))

    for key, value, rule, only_runtime in HIPAA_NEGATIVES:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(HIPAA_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("hipaa", "negative", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "FAIL", rule,
                            HIPAA_BASELINE))

    for key, value, rule, only_runtime in HIPAA_STRUCTURAL:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(HIPAA_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("hipaa", "structural", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "PASS", rule,
                            HIPAA_BASELINE))
    return rows


# ---------------------------------------------------------------------------
# PCI-DSS
# ---------------------------------------------------------------------------
# logRetentionDays=365 is PCI-DSS Req 10.7's own floor (ONE_YEAR) -- same numeric floor as SOC2's,
# kept as its own constant since the two frameworks' rules are independent.
PCIDSS_BASELINE = {
    "complianceFrameworks": "PCI-DSS",
    "auditManagerEnabled": True,
    "awsConfigEnabled": True,
    "enableSsl": True,
    "wafEnabled": True,
    "networkMode": "private-with-nat",
    "logRetentionDays": 365,
    "cognitoAutoProvision": True,
    "cognitoMfaEnabled": True,
    "guardDutyEnabled": True,  # THREAT_DETECTION is REQUIRED for PCI-DSS
    "inspectorEnabled": True,  # VULNERABILITY_SCANNING is REQUIRED for PCI-DSS
    # ThreatProtectionRules: FARGATE+GuardDuty auto-satisfies anti-malware/file-integrity via
    # immutable infrastructure, but EC2 has no such structural guarantee and needs these explicit.
    "antiMalwareEnabled": True,
    "fileIntegrityMonitoring": True,
}

PCIDSS_NEGATIVES = [
    ("enableSsl", False, "PCI-DSS-Req-4.1-SSL", None),
    ("networkMode", "public-no-nat", "PCI-DSS-Req-1.3-Network", None),
    ("authMode", "none", "PCI-DSS-Req-8.2-Auth", None),
    ("wafEnabled", False, "PCI-DSS-Req-6.6-WAF", None),
    ("logRetentionDays", 30, "PCI-DSS-Req-10.7-Retention", None),
    ("cognitoMfaEnabled", False, "PCI-DSS-Req-8.3-MFA", None),
]

# Controls PCI-DSS checks (PciDssRules.java) that ComplianceMatrix marks REQUIRED for PCI-DSS.
# PciDssRules.java never references isMultiAzEnforced/isAutoScalingEnabled, so there's no PCI-DSS
# rule to pair those keys with. EC2_IMDSV2 is ADVISORY for PCI-DSS -- see PCIDSS_ADVISORY below,
# now that PciDssRules#validateMatrixControls checks it (PCI-DSS-Req-2.2-IMDSv2).
PCIDSS_STRUCTURAL = [
    ("ebsEncryptionEnabled", False, "PCI-DSS-Req-3.4-EBS", None),
    ("efsEncryptionAtRestEnabled", False, "PCI-DSS-Req-3.4-EFS", None),
    ("s3EncryptionEnabled", False, "PCI-DSS-Req-3.4-S3", None),
    ("efsEncryptionInTransitEnabled", False, "PCI-DSS-Req-4.1-EFS-Transit", None),
    ("cloudTrailEnabled", False, "PCI-DSS-Req-10.2-CloudTrail", None),
    ("enableFlowlogs", False, "PCI-DSS-Req-10.3-FlowLogs", None),
    ("albAccessLogging", False, "PCI-DSS-Req-10.5-ALB", None),
    ("s3ObjectLockEnabled", False, "PCI-DSS-Req-10.7-AuditLogImmutability", None),
    ("guardDutyEnabled", False, "PCI-DSS-Req-11.4-GuardDuty", None),
    ("securityMonitoringEnabled", False, "PCI-DSS-Req-11.5-Monitoring", None),
    ("awsConfigEnabled", False, "PCI-DSS-Req-11.6-Config", None),
    ("cloudWatchLogsKmsEncryptionEnabled", False, "PCI-DSS-Req-3.4-LogEncryption", None),
    ("snsKmsEncryptionEnabled", False, "MESSAGING-ENCRYPTION", None),
]

PCIDSS_ADVISORY = [
    ("imdsv2Required", False, "PCI-DSS-Req-2.2-IMDSv2", "EC2"),
]


def pcidss_rows():
    apps = load_inventory()
    rows = []
    for app in apps:
        modes = app["authModes"].split(",")
        auth = "application-oidc" if "application-oidc" in modes else ("alb-oidc" if "alb-oidc" in modes else None)
        for runtime in RUNTIMES:
            for profile in PROFILES:
                overrides = dict(PCIDSS_BASELINE)
                if app["database"] in ("REQUIRED", "OPTIONAL"):
                    overrides["provisionDatabase"] = True
                if auth:
                    overrides["authMode"] = auth
                    expected, rule = "PASS", ""
                else:
                    overrides["authMode"] = "none"
                    expected, rule = "FAIL", "PCI-DSS-Req-8.2-Auth"
                    if profile != "PRODUCTION" or runtime != "FARGATE":
                        continue
                rows.append(row("pci-dss", "app", app["applicationId"], runtime, profile, overrides, expected, rule,
                                PCIDSS_BASELINE))

    for runtime in RUNTIMES:
        overrides = dict(PCIDSS_BASELINE)
        overrides["authMode"] = "application-oidc"
        rows.append(row("pci-dss", "dev-skip", NEGATIVE_SUBJECT, runtime, "DEV", overrides, "SKIP", "",
                        PCIDSS_BASELINE))

    for key, value, rule, only_runtime in PCIDSS_NEGATIVES:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(PCIDSS_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("pci-dss", "negative", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "FAIL", rule,
                            PCIDSS_BASELINE))

    for key, value, rule, only_runtime in PCIDSS_STRUCTURAL:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(PCIDSS_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("pci-dss", "structural", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "PASS",
                            rule, PCIDSS_BASELINE))

    for key, value, rule, only_runtime in PCIDSS_ADVISORY:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(PCIDSS_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("pci-dss", "advisory", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "ADVISORY",
                            rule, PCIDSS_BASELINE))
    return rows


# ---------------------------------------------------------------------------
# GDPR
# ---------------------------------------------------------------------------
# logRetentionDays=90 is GDPR's own floor (GdprRules#isRetentionSufficient accepts THREE_MONTHS or
# longer). gdprDataTransferApproved=True is required in the baseline itself (not just as a negative
# lever): GDPR-DATA-RESIDENCY fails for any non-"eu-*" region, and this matrix deploys to
# us-east-1 on LocalStack, so without it every GDPR app row would fail synthesis before ever
# reaching the control(s) actually under test.
GDPR_BASELINE = {
    "complianceFrameworks": "GDPR",
    "auditManagerEnabled": True,
    "awsConfigEnabled": True,
    "enableSsl": True,
    "wafEnabled": True,
    "networkMode": "private-with-nat",
    "logRetentionDays": 90,
    "gdprDataTransferApproved": True,
    "macieEnabled": True,  # AdvancedMonitoringRules checks this unconditionally for GDPR/HIPAA
    "macieAutomatedDiscovery": True,  # required once macieEnabled is true, same GDPR/HIPAA check
}

# GDPR has no MFA rule (grepped: no reference to cognitoMfaEnabled in GdprRules.java), so unlike
# HIPAA/PCI-DSS/FedRAMP there is no cognitoMfaEnabled negative here.
GDPR_NEGATIVES = [
    ("enableSsl", False, "GDPR-SSL-ENCRYPTION", None),
    ("networkMode", "public-no-nat", "GDPR-NETWORK-ISOLATION", None),
    ("authMode", "none", "GDPR-AUTHENTICATION", None),
    ("wafEnabled", False, "GDPR-WAF-PROTECTION", None),
    ("logRetentionDays", 30, "GDPR-LOG-RETENTION", None),
    ("gdprDataTransferApproved", False, "GDPR-DATA-RESIDENCY", None),
]

# Controls GDPR checks (GdprRules.java) that ComplianceMatrix marks REQUIRED for GDPR. GuardDuty
# (THREAT_DETECTION) was ADVISORY-but-silent (see GDPR_ADVISORY note below for the ones now fixed);
# ebsEncryptionEnabled/efsEncryptionAtRestEnabled/s3EncryptionEnabled have real override fields, but
# GDPR marks ENCRYPTION_AT_REST REQUIRED, so the override is still moot here.
GDPR_STRUCTURAL = [
    ("ebsEncryptionEnabled", False, "GDPR-EBS-ENCRYPTION", None),
    ("efsEncryptionAtRestEnabled", False, "GDPR-EFS-ENCRYPTION", None),
    ("s3EncryptionEnabled", False, "GDPR-S3-ENCRYPTION", None),
    ("efsEncryptionInTransitEnabled", False, "GDPR-EFS-TRANSIT-ENCRYPTION", None),
    ("cloudTrailEnabled", False, "GDPR-CLOUDTRAIL", None),
    ("enableFlowlogs", False, "GDPR-FLOW-LOGS", None),
    ("albAccessLogging", False, "GDPR-ALB-LOGGING", None),
    ("securityMonitoringEnabled", False, "GDPR-SECURITY-MONITORING", None),
    ("automatedBackupEnabled", False, "GDPR-AUTOMATED-BACKUP", None),
    ("awsConfigEnabled", False, "GDPR-AWS-CONFIG", None),
    ("cloudWatchLogsKmsEncryptionEnabled", False, "GDPR-Art32-LogEncryption", None),
]

# SNS_KMS_ENCRYPTION, EC2_IMDSV2 and THREAT_DETECTION are ADVISORY for GDPR, and each now produces
# a real ComplianceRule.advisory() finding when off instead of silence: MessagingSecurityRules for
# SNS-KMS, GdprRules#validateMatrixControls for IMDSv2 (GDPR-IMDSV2), and
# GdprRules#validateBreachDetection for GuardDuty (GDPR-GUARDDUTY).
GDPR_ADVISORY = [
    ("snsKmsEncryptionEnabled", False, "MESSAGING-ENCRYPTION", None),
    ("imdsv2Required", False, "GDPR-IMDSV2", "EC2"),
    ("guardDutyEnabled", False, "GDPR-GUARDDUTY", None),
]


def gdpr_rows():
    apps = load_inventory()
    rows = []
    for app in apps:
        modes = app["authModes"].split(",")
        auth = "application-oidc" if "application-oidc" in modes else ("alb-oidc" if "alb-oidc" in modes else None)
        for runtime in RUNTIMES:
            for profile in PROFILES:
                overrides = dict(GDPR_BASELINE)
                if app["database"] in ("REQUIRED", "OPTIONAL"):
                    overrides["provisionDatabase"] = True
                if auth:
                    overrides["authMode"] = auth
                    expected, rule = "PASS", ""
                else:
                    overrides["authMode"] = "none"
                    expected, rule = "FAIL", "GDPR-AUTHENTICATION"
                    if profile != "PRODUCTION" or runtime != "FARGATE":
                        continue
                rows.append(row("gdpr", "app", app["applicationId"], runtime, profile, overrides, expected, rule,
                                GDPR_BASELINE))

    for runtime in RUNTIMES:
        overrides = dict(GDPR_BASELINE)
        overrides["authMode"] = "application-oidc"
        rows.append(row("gdpr", "dev-skip", NEGATIVE_SUBJECT, runtime, "DEV", overrides, "SKIP", "", GDPR_BASELINE))

    for key, value, rule, only_runtime in GDPR_NEGATIVES:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(GDPR_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("gdpr", "negative", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "FAIL", rule,
                            GDPR_BASELINE))

    for key, value, rule, only_runtime in GDPR_STRUCTURAL:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(GDPR_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("gdpr", "structural", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "PASS", rule,
                            GDPR_BASELINE))

    for key, value, rule, only_runtime in GDPR_ADVISORY:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(GDPR_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("gdpr", "advisory", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "ADVISORY",
                            rule, GDPR_BASELINE))
    return rows


# ---------------------------------------------------------------------------
# FedRAMP (Moderate)
# ---------------------------------------------------------------------------
# logRetentionDays=1096 is FedRAMP's own floor (FedRampRules#isRetentionSufficient accepts
# RetentionDays.THREE_YEARS -- AWS's actual value 1096 days -- or longer).
# No enableSsl negative: unlike the other three frameworks, FedRampRules never reads
# ctx.cfc.enableSsl() directly -- its SC-8 transmission-confidentiality rule checks certificate
# presence (ctx.cert.get().isEmpty()) instead, which this matrix's overrides can't drive directly.
FEDRAMP_BASELINE = {
    "complianceFrameworks": "FEDRAMP",
    "auditManagerEnabled": True,
    "awsConfigEnabled": True,
    "enableSsl": True,
    "wafEnabled": True,
    "networkMode": "private-with-nat",
    "logRetentionDays": 1096,
    "cognitoAutoProvision": True,
    "cognitoMfaEnabled": True,
    "guardDutyEnabled": True,  # THREAT_DETECTION is REQUIRED for FEDRAMP
    "securityHubEnabled": True,  # SECURITY_HUB is REQUIRED for FEDRAMP
    "inspectorEnabled": True,  # VULNERABILITY_SCANNING is REQUIRED for FEDRAMP
}

FEDRAMP_NEGATIVES = [
    ("networkMode", "public-no-nat", "FEDRAMP-AC-17", None),
    ("authMode", "none", "FEDRAMP-IA-2", None),
    ("wafEnabled", False, "FEDRAMP-SC-7-WAF", None),
    ("logRetentionDays", 365, "FEDRAMP-AU-11", None),
    ("cognitoMfaEnabled", False, "FEDRAMP-IA-2(1)", None),
]

# Controls FedRAMP checks (FedRampRules.java) that ComplianceMatrix marks REQUIRED for FEDRAMP.
# FedRampRules.java never references isAutoScalingEnabled, so there's no rule to pair that key with.
# ebsEncryptionEnabled/efsEncryptionAtRestEnabled/s3EncryptionEnabled/imdsv2Required now have real
# override fields, but FedRAMP marks ENCRYPTION_AT_REST and EC2_IMDSV2 REQUIRED, so the override
# is still moot here.
FEDRAMP_STRUCTURAL = [
    ("imdsv2Required", False, "FEDRAMP-AC-3-IMDSv2", "EC2"),
    ("ebsEncryptionEnabled", False, "FEDRAMP-MP-4-EBS", None),
    ("efsEncryptionAtRestEnabled", False, "FEDRAMP-MP-4-EFS", None),
    ("efsEncryptionInTransitEnabled", False, "FEDRAMP-MP-5-EFS", None),
    ("s3EncryptionEnabled", False, "FEDRAMP-AU-9", None),
    ("enableFlowlogs", False, "FEDRAMP-AC-4", None),
    ("albAccessLogging", False, "FEDRAMP-AU-12-ALB", None),
    ("guardDutyEnabled", False, "FEDRAMP-AU-6", None),
    ("securityMonitoringEnabled", False, "FEDRAMP-CA-7(4)", None),
    ("awsConfigEnabled", False, "FEDRAMP-CA-7", None),
    ("cloudWatchLogsKmsEncryptionEnabled", False, "FEDRAMP-AU-9-LogEncryption", None),
    ("s3ObjectLockEnabled", False, "FEDRAMP-AU-9-AuditLogImmutability", None),
    ("automatedBackupEnabled", False, "FEDRAMP-CP-9", None),
    ("crossRegionBackupEnabled", False, "FEDRAMP-CP-6", None),
    ("multiAzEnforced", False, "FEDRAMP-CP-10", None),
    ("cloudTrailEnabled", False, "FEDRAMP-AU-2", None),
    ("snsKmsEncryptionEnabled", False, "MESSAGING-ENCRYPTION", None),
]


def fedramp_rows():
    apps = load_inventory()
    rows = []
    for app in apps:
        modes = app["authModes"].split(",")
        auth = "application-oidc" if "application-oidc" in modes else ("alb-oidc" if "alb-oidc" in modes else None)
        for runtime in RUNTIMES:
            for profile in PROFILES:
                overrides = dict(FEDRAMP_BASELINE)
                if app["database"] in ("REQUIRED", "OPTIONAL"):
                    overrides["provisionDatabase"] = True
                if auth:
                    overrides["authMode"] = auth
                    expected, rule = "PASS", ""
                else:
                    overrides["authMode"] = "none"
                    expected, rule = "FAIL", "FEDRAMP-IA-2"
                    if profile != "PRODUCTION" or runtime != "FARGATE":
                        continue
                rows.append(row("fedramp", "app", app["applicationId"], runtime, profile, overrides, expected, rule,
                                FEDRAMP_BASELINE))

    for runtime in RUNTIMES:
        overrides = dict(FEDRAMP_BASELINE)
        overrides["authMode"] = "application-oidc"
        rows.append(row("fedramp", "dev-skip", NEGATIVE_SUBJECT, runtime, "DEV", overrides, "SKIP", "",
                        FEDRAMP_BASELINE))

    for key, value, rule, only_runtime in FEDRAMP_NEGATIVES:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(FEDRAMP_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("fedramp", "negative", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "FAIL", rule,
                            FEDRAMP_BASELINE))

    for key, value, rule, only_runtime in FEDRAMP_STRUCTURAL:
        for runtime in RUNTIMES:
            if only_runtime and runtime != only_runtime:
                continue
            overrides = dict(FEDRAMP_BASELINE)
            overrides["authMode"] = "application-oidc"
            overrides[key] = value
            rows.append(row("fedramp", "structural", NEGATIVE_SUBJECT, runtime, "PRODUCTION", overrides, "PASS",
                            rule, FEDRAMP_BASELINE))
    return rows


def row(framework, tier, app, runtime, profile, overrides, expected, rule, baseline):
    tail = ""
    if tier in ("negative", "structural", "advisory"):
        changed = [k for k, v in overrides.items() if baseline.get(k) != v and k != "authMode"] or ["authMode"]
        tail = "-" + changed[0]
    row_id = f"{framework}-{tier}-{app}-{runtime.lower()}-{profile.lower()}{tail}"
    data = {c: "" for c in COLUMNS}
    data.update(rowId=row_id, framework=framework, tier=tier, app=app, runtime=runtime, profile=profile,
                authMode=overrides.get("authMode", ""), expected=expected, expectedRule=rule,
                overrides=json.dumps(overrides, sort_keys=True), status="PENDING")
    return data


def write(framework, rows):
    path = MATRIX_DIR / f"{framework}-matrix.csv"
    previous = {}
    if path.exists():
        with open(path, newline="") as f:
            previous = {r["rowId"]: r for r in csv.DictReader(f)}
    kept = 0
    for r in rows:
        old = previous.get(r["rowId"])
        if old and old["overrides"] == r["overrides"]:
            for col in ("status", "checks", "deployStatus", "verified", "verifiedAt", "notes"):
                r[col] = old[col]
            kept += 1
    with open(path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    return path, kept


def merge(framework, results_path):
    """Folds a runner results TSV (rowId, result, checks, detail) into the tracker's synthesis columns."""
    import datetime
    path = MATRIX_DIR / f"{framework}-matrix.csv"
    with open(path, newline="") as f:
        rows = list(csv.DictReader(f))
    results = {}
    with open(results_path) as f:
        next(f)
        for line in f:
            parts = line.rstrip("\n").split("\t")
            results[parts[0]] = parts + [""] * (4 - len(parts))
    today = datetime.date.today().isoformat()
    updated = 0
    for r in rows:
        res = results.get(r["rowId"])
        if not res:
            continue
        verdict = res[1]
        r["status"] = "PASS" if verdict == "PASS" else ("FINDING" if verdict.startswith("FINDING") else
                                                          ("ERROR" if verdict == "ERROR" else "FAIL"))
        r["checks"] = res[2]
        r["notes"] = (verdict + ": " + res[3]).strip(": ") if verdict != "PASS" else res[3]
        r["verifiedAt"] = today
        updated += 1
    with open(path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    print(f"merged {updated} results into {path}")


def merge_verify(framework, verify_path):
    """Folds template-verifier output (rowId, rule, OK|MISMATCH|N/A, detail) into the `verified` column."""
    path = MATRIX_DIR / f"{framework}-matrix.csv"
    with open(path, newline="") as f:
        rows = list(csv.DictReader(f))
    per_row = {}
    with open(verify_path) as f:
        next(f)
        for line in f:
            parts = line.rstrip("\n").split("\t")
            per_row.setdefault(parts[0], []).append(parts + [""] * (4 - len(parts)))
    for r in rows:
        checks = per_row.get(r["rowId"])
        if not checks:
            continue
        ok = sum(1 for c in checks if c[2] == "OK")
        bad = [c for c in checks if c[2] == "MISMATCH"]
        r["verified"] = f"{ok} ok" + (f", {len(bad)} MISMATCH" if bad else "")
        note = "; ".join(f"{c[1]}: {c[3]}" for c in bad)
        r["notes"] = (r["notes"] + " | " if r["notes"] else "") + note if note else r["notes"]
    with open(path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    print(f"merged template verification for {len(per_row)} rows into {path}")


def main():
    if len(sys.argv) > 3 and sys.argv[1] == "merge-verify":
        merge_verify(sys.argv[2], sys.argv[3])
        return
    if len(sys.argv) > 3 and sys.argv[1] == "merge":
        merge(sys.argv[2], sys.argv[3])
        return
    generators = {
        "soc2": soc2_rows,
        "hipaa": hipaa_rows,
        "pci-dss": pcidss_rows,
        "gdpr": gdpr_rows,
        "fedramp": fedramp_rows,
    }
    framework = sys.argv[1] if len(sys.argv) > 1 else "soc2"
    if framework not in generators:
        sys.exit(f"unknown framework {framework!r}, expected one of {sorted(generators)}")
    rows = generators[framework]()
    path, kept = write(framework, rows)
    by_tier = {}
    for r in rows:
        key = (r["tier"], r["expected"])
        by_tier[key] = by_tier.get(key, 0) + 1
    print(f"wrote {len(rows)} rows to {path} ({kept} kept previous results)")
    for (tier, expected), n in sorted(by_tier.items()):
        print(f"  {tier:9} {expected:5} {n}")


if __name__ == "__main__":
    main()
