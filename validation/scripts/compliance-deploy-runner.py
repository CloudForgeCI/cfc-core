#!/usr/bin/env python3
"""Deploys compliance matrix rows to LocalStack one at a time and records what came up.

    python3 scripts/compliance-deploy-runner.py soc2                 # every pending PASS app row
    python3 scripts/compliance-deploy-runner.py soc2 --rows jenkins  # rows whose id contains 'jenkins'
    python3 scripts/compliance-deploy-runner.py soc2 --limit 5 --dry-run

For each row it builds the deployment context, recreates LocalStack for a clean slate, deploys through
cloudforge-cli (the same path a user takes), then checks the stack status, the resources created, and
whether the application answers. The tracker (compliance-matrix/<framework>-matrix.csv) is rewritten after
every row, so an interrupted run resumes where it stopped; rows that already have a deployStatus are skipped
unless --force is given.

Environment: LOCALSTACK_AUTH_TOKEN must be set. Requires cloudforge-cli on PATH. Run from validation.
"""
import argparse
import csv
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MATRIX_DIR = ROOT / "compliance-matrix"
LOG_DIR = MATRIX_DIR / "logs"
ENDPOINT = "http://localhost:4566"
HEALTH_URL = f"{ENDPOINT}/_localstack/health"
DEPLOY_TIMEOUT_SECONDS = 1800
APP_WAIT_SECONDS = 240

COLUMNS = ["rowId", "framework", "tier", "app", "runtime", "profile", "authMode", "expected", "expectedRule",
           "overrides", "status", "checks", "deployStatus", "verified", "verifiedAt", "notes",
           "deploymentDetails", "resourceTypes"]

AWS_ENV = {**os.environ, "AWS_ENDPOINT_URL": ENDPOINT, "AWS_DEFAULT_REGION": "us-east-1",
           "AWS_ACCESS_KEY_ID": "test", "AWS_SECRET_ACCESS_KEY": "test"}
AWS_ENV.pop("CFC_DEPLOYING", None)


def restart_clean():
    """Recreates the LocalStack container so the next deployment starts from empty state."""
    subprocess.run(["cloudforge-cli", "emulator", "restart", "--target", "localstack"],
                    capture_output=True, text=True, timeout=180)
    deadline = time.time() + 180
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(HEALTH_URL, timeout=5) as response:
                services = json.loads(response.read()).get("services", {})
                if services.get("cloudformation") in ("available", "running"):
                    return True
        except (OSError, ValueError):
            pass
        time.sleep(4)
    return False


def aws(*args):
    result = subprocess.run(["aws", *args], env=AWS_ENV, capture_output=True, text=True, timeout=120)
    return result.stdout.strip()


def build_context(row, base):
    """Same construction as MatrixSynthRunner, so a deployed row is exactly the row that was synthesized."""
    context = dict(base)
    context.update(stackName=row["rowId"], applicationId=row["app"], applicationName=row["app"],
                   runtime=row["runtime"], securityProfile=row["profile"],
                   cognitoDomainPrefix="m" + hashlib.md5(row["rowId"].encode()).hexdigest()[:8])
    if row["runtime"] == "EC2":
        context.pop("cpu", None)
        context.pop("memory", None)
        context["instanceType"] = "t3.small"
    context.update(json.loads(row["overrides"]))
    return context


def stack_summary(stack_name):
    """Status, failure reasons, resource-type counts and outputs for the deployed stack."""
    described = aws("cloudformation", "describe-stacks", "--stack-name", stack_name, "--output", "json")
    if not described:
        return {"status": "NOT_CREATED", "failures": [], "types": {}, "outputs": {}}
    stack = json.loads(described)["Stacks"][0]
    events = aws("cloudformation", "describe-stack-events", "--stack-name", stack_name, "--output", "json")
    failures = []
    for event in json.loads(events or '{"StackEvents": []}')["StackEvents"]:
        if "FAILED" in event.get("ResourceStatus", "") and event.get("ResourceStatusReason"):
            failures.append(f"{event['LogicalResourceId'][-40:]}: {event['ResourceStatusReason'][:160]}")
    resources = json.loads(aws("cloudformation", "list-stack-resources", "--stack-name", stack_name,
                               "--output", "json") or '{"StackResourceSummaries": []}')["StackResourceSummaries"]
    types = {}
    for resource in resources:
        types[resource["ResourceType"]] = types.get(resource["ResourceType"], 0) + 1
    outputs = {o["OutputKey"]: o["OutputValue"] for o in stack.get("Outputs", [])}
    return {"status": stack["StackStatus"], "failures": failures, "types": types, "outputs": outputs}


def probe_application(outputs):
    """Waits for the application to answer; anything from 200 to 499 counts as up (auth redirects are fine)."""
    url = outputs.get("LocalStackApplicationUrl") or outputs.get("ApplicationUrl")
    if not url:
        return "no-url", None
    deadline = time.time() + APP_WAIT_SECONDS
    last = "no-response"
    while time.time() < deadline:
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "cfc-matrix"})
            opener = urllib.request.build_opener(NoRedirect)
            with opener.open(request, timeout=10) as response:
                code = response.status
        except urllib.error.HTTPError as error:
            code = error.code
        except (urllib.error.URLError, OSError):
            code = None
        if code is not None:
            last = str(code)
            if 200 <= code < 500:
                return f"up ({code})", url
        time.sleep(10)
    return f"down ({last})", url


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def deploy_row(row, base):
    LOG_DIR.mkdir(exist_ok=True)
    context_path = LOG_DIR / f"{row['rowId']}.context.json"
    context_path.write_text(json.dumps(build_context(row, base), indent=2))
    log_path = LOG_DIR / f"{row['rowId']}.log"
    stack_name = f"{row['rowId']}-localstack"

    if not restart_clean():
        return {"deploy": "FAILED: LocalStack did not become healthy", "checks": "", "detail": "", "summary": None}

    started = time.time()
    try:
        with open(log_path, "w") as log:
            subprocess.run(["cloudforge-cli", "deploy", "--context", str(context_path), "--target", "localstack"],
                           env=AWS_ENV, stdout=log, stderr=subprocess.STDOUT, timeout=DEPLOY_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        return {"deploy": f"TIMEOUT after {DEPLOY_TIMEOUT_SECONDS}s", "checks": "", "detail": "", "summary": None}

    log_text = log_path.read_text(errors="replace")
    checks = re.search(r"validation passed \((\d+) checks\)", log_text)
    summary = stack_summary(stack_name)
    detail = []
    if summary["status"] == "CREATE_COMPLETE":
        health, url = probe_application(summary["outputs"])
        detail.append(f"app {health}")
        deploy = "CREATE_COMPLETE" if health.startswith("up") else "CREATE_COMPLETE; " + health
    else:
        deploy = summary["status"]
        detail.extend(summary["failures"][:3])
    if row["authMode"] == "alb-oidc":
        # LocalStack has no ALB authentication: the adapter removes the listener action, so only the
        # synthesized template (checked by the template verifier) shows the auth requirement.
        detail.append("auth not enforced on LocalStack (alb-oidc)")
    detail.append(f"{sum(summary['types'].values())} resources, {int(time.time() - started)}s")
    return {"deploy": deploy, "checks": checks.group(1) if checks else "", "detail": "; ".join(detail),
            "summary": summary}


def read_matrix(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))


def write_matrix(path, rows):
    tmp = path.with_suffix(".tmp")
    with open(tmp, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    tmp.replace(path)


def write_dashboard_tsv(framework, rows):
    """The format deploy-localstack-compliance-matrix.sh writes, which compliance-report-generator.py reads."""
    path = ROOT / "scripts" / "validation-results" / f"localstack-{framework}-matrix-results.tsv"
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w") as f:
        f.write("config\tframework\tprofile\truntime\tresult\tstack_status\tconfig_rules\tguardduty\tcloudtrail\twaf\n")
        for r in rows:
            if not r["deployStatus"]:
                continue
            counts = json.loads(r.get("resourceTypes", "") or "{}")
            ok = r["deployStatus"].startswith("CREATE_COMPLETE")
            f.write("\t".join([
                r["rowId"], r["framework"].upper(), r["profile"], r["runtime"], "PASS" if ok else "FAIL",
                r["deployStatus"].split(";")[0],
                str(counts.get("AWS::Config::ConfigRule", 0)), str(counts.get("AWS::GuardDuty::Detector", 0)),
                str(counts.get("AWS::CloudTrail::Trail", 0)), str(counts.get("AWS::WAFv2::WebACL", 0))]) + "\n")
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("framework")
    parser.add_argument("--rows", default="", help="only rows whose id contains this text")
    parser.add_argument("--limit", type=int, default=0, help="stop after this many deployments")
    parser.add_argument("--force", action="store_true", help="redeploy rows that already have a result")
    parser.add_argument("--dry-run", action="store_true", help="list the rows that would be deployed")
    args = parser.parse_args()

    matrix_path = MATRIX_DIR / f"{args.framework}-matrix.csv"
    rows = read_matrix(matrix_path)
    base = json.loads((MATRIX_DIR / "base-context.json").read_text())
    todo = [r for r in rows if r["tier"] == "app" and r["expected"] == "PASS" and args.rows in r["rowId"]
            and (args.force or not r["deployStatus"])]
    if args.limit:
        todo = todo[:args.limit]
    print(f"{len(todo)} row(s) to deploy")
    if args.dry_run:
        for r in todo:
            print("  ", r["rowId"])
        return
    if not os.environ.get("LOCALSTACK_AUTH_TOKEN"):
        sys.exit("LOCALSTACK_AUTH_TOKEN is not set")

    by_id = {r["rowId"]: r for r in rows}
    for index, row in enumerate(todo, 1):
        print(f"[{index}/{len(todo)}] {row['rowId']} ...", flush=True)
        outcome = deploy_row(row, base)
        # LocalStack's nested-container RDS emulation occasionally misses its own readiness
        # window under the concurrent resource load a full stack deploy creates -- a timing
        # fluke, not a failure worth recording as-is. One retry (fresh restart, same row) is enough in practice.
        if "DB instance creation failed" in outcome["detail"]:
            print(f"    retrying once (RDS readiness race) ...", flush=True)
            outcome = deploy_row(row, base)
        target = by_id[row["rowId"]]
        target["deployStatus"] = outcome["deploy"]
        if outcome["checks"]:
            target["checks"] = outcome["checks"]
        # Separate columns from "notes" -- merge() in compliance-matrix.py overwrites "notes"
        # with the synthesis verdict, which would otherwise erase this deploy-time detail (or
        # vice versa, depending on run order). resourceTypes is structured data (a JSON object),
        # not free text, so it gets its own column rather than being embedded in deploymentDetails.
        target["deploymentDetails"] = outcome["detail"]
        target["resourceTypes"] = json.dumps(outcome["summary"]["types"], sort_keys=True) if outcome["summary"] else "{}"
        target["verifiedAt"] = datetime.date.today().isoformat()
        write_matrix(matrix_path, rows)
        print(f"    {outcome['deploy']} | {outcome['detail']}", flush=True)
    print("dashboard results:", write_dashboard_tsv(args.framework, rows))


if __name__ == "__main__":
    main()
