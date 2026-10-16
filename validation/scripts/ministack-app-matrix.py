#!/usr/bin/env python3
"""Deploys every registered CloudForge application to MiniStack one at a time and compares what the
platform generated with what MiniStack actually created.

    python3 scripts/ministack-app-matrix.py                          # all applications
    python3 scripts/ministack-app-matrix.py --apps redis,jenkins     # a subset
    python3 scripts/ministack-app-matrix.py --preflight off          # skip the deploy preflight (see what really breaks)
    python3 scripts/ministack-app-matrix.py --db-versions            # every database engine/version each application lists

For each application it records, under ministack-matrix/<run>/<app>/:
  context.json                the deployment context used
  canonical.template.json     the CloudFormation template CDK synthesized (from the synth sidecar)
  deploy.json                 the cloudforge-cli deploy output lines
  deployed.template.json      the template MiniStack holds for the stack (after the MiniStack adapter)
  stack-resources.json        CloudFormation's own resource list for the stack
  live-inventory.json         each resource checked against the emulator's service API
  comparison.json             canonical vs deployed vs created vs live, resource by resource
and writes summary.md / summary.csv at the top of the run directory.

Compares three layers so a gap is attributed to the right stage:
  canonical -> deployed       what the MiniStack adapter removed, added or retyped
  deployed  -> created        what CloudFormation did not create, or did not complete
  created   -> live           what the emulator's own APIs cannot find

--db-versions deploys each database application once per engine/version in its supportedDatabases() list
(from the inventory's databaseOptions column) with a database provisioned, and records the engine, version and
status of the database MiniStack actually started. It skips the health wait: the question there is whether that
engine version comes up and the application's task starts against it, not how long the application takes to boot.

Stacks are destroyed after each application and leftover emulator containers removed. An application's
stack is always named MS-<applicationId>; stacks with other names are never touched.

Requires: cloudforge-cli on PATH, MiniStack running on localhost:4566, the synth sidecar on :8090
(scripts/start-synth-sidecar.sh in cloudforge-manager), and the AWS CLI. Run from validation.
"""
import argparse
import csv
import datetime
import io
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_ROOT = ROOT / "ministack-matrix"
ENDPOINT = os.environ.get("MINISTACK_ENDPOINT", "http://localhost:4566")
SIDECAR = os.environ.get("SYNTH_SERVICE_URL", "http://localhost:8090")
DEPLOY_TIMEOUT = 900
DESTROY_TIMEOUT = 600
HEALTH_WAIT = int(os.environ.get("MATRIX_HEALTH_WAIT", "300"))
SETTLE_SECONDS = 20

# Larger tasks the application plugins recommend.
MEMORY = {"nexus": 4096, "sonarqube": 4096, "gitlab": 8192}

ENV = {**os.environ, "AWS_ENDPOINT_URL": ENDPOINT, "AWS_DEFAULT_REGION": "us-east-1",
       "AWS_ACCESS_KEY_ID": "test", "AWS_SECRET_ACCESS_KEY": "test"}
ENV.pop("AWS_PROFILE", None)
ENV.pop("CFC_DEPLOYING", None)


def aws(*args, timeout=60):
    """Runs the AWS CLI against the emulator; returns parsed JSON, or None on any failure."""
    result = subprocess.run(["aws", "--endpoint-url", ENDPOINT, *args, "--output", "json"],
                            capture_output=True, text=True, env=ENV, timeout=timeout)
    if result.returncode != 0 or not result.stdout.strip():
        return None
    try:
        return json.loads(result.stdout)
    except ValueError:
        return None


def load_inventory(path):
    rows = []
    with open(path) as handle:
        header = handle.readline().rstrip("\n").split("\t")
        for line in handle:
            values = line.rstrip("\n").split("\t")
            rows.append(dict(zip(header, values)))
    return rows


def context_for(app, option=None):
    app_id = app["applicationId"]
    context = {
        "stackName": f"MS-{app_id}",
        "applicationId": app_id,
        "applicationName": app_id,
        "environment": "development",
        "runtime": "FARGATE",
        "authMode": "none",
    }
    if app["database"] == "REQUIRED" or option:
        context["provisionDatabase"] = True
    if option:
        context["databaseEngine"], context["databaseVersion"] = option
    if app_id in MEMORY:
        context["memory"] = MEMORY[app_id]
    return context


def synthesize(context):
    """Canonical template from the synth sidecar, with the emulator account the CLI would set."""
    body = dict(context, account="000000000000", region="us-east-1")
    request = urllib.request.Request(f"{SIDECAR}/v1/synthesize", data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            payload = response.read()
    except urllib.error.HTTPError as error:
        return None, f"HTTP {error.code}: {error.read().decode()[:300]}"
    except OSError as error:
        return None, f"sidecar unreachable: {error}"
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        names = [n for n in archive.namelist() if n.endswith(".template.json") and "/" not in n]
        if not names:
            return None, "no template in the synthesized assembly"
        stack_template = next((n for n in names if n.startswith(context["stackName"])), names[0])
        return json.loads(archive.read(stack_template)), None


def run_cli(verb, context_path, cwd, timeout, extra_env=None):
    env = {**ENV, **(extra_env or {})}
    try:
        result = subprocess.run(["cloudforge-cli", verb, "--context", str(context_path), "--target", "ministack"],
                                capture_output=True, text=True, cwd=cwd, env=env, timeout=timeout)
        return result.returncode, result.stdout
    except subprocess.TimeoutExpired as error:
        return 124, (error.stdout.decode() if isinstance(error.stdout, bytes) else (error.stdout or ""))


def json_lines(text):
    out = []
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("{"):
            try:
                out.append(json.loads(line))
            except ValueError:
                pass
    return out


def resources_of(template):
    return {rid: body.get("Type", "?") for rid, body in (template or {}).get("Resources", {}).items()}


def get_template(stack):
    data = aws("cloudformation", "get-template", "--stack-name", stack)
    if not data:
        return None
    body = data.get("TemplateBody")
    return json.loads(body) if isinstance(body, str) else body


# --- live checks: resource type -> function(physical id) -> (ok, detail) ---------------------------------

def _described(call, key, *args):
    data = aws(*call, *args)
    items = (data or {}).get(key) or []
    return (bool(items), f"{len(items)} found")


def _ecs_service(physical):
    parts = physical.split("/")
    cluster = parts[-2] if len(parts) >= 3 else None
    if not cluster:
        return False, "cannot derive the cluster from the service id"
    data = aws("ecs", "describe-services", "--cluster", cluster, "--services", physical)
    services = (data or {}).get("services") or []
    if not services or services[0].get("status") in (None, "INACTIVE"):
        return False, "service not found or inactive"
    svc = services[0]
    return True, f"{svc.get('status')} running={svc.get('runningCount')} desired={svc.get('desiredCount')}"


def _ecs_cluster(physical):
    data = aws("ecs", "describe-clusters", "--clusters", physical)
    clusters = (data or {}).get("clusters") or []
    return (bool(clusters) and clusters[0].get("status") == "ACTIVE", clusters[0].get("status") if clusters else "not found")


def _log_group(physical):
    data = aws("logs", "describe-log-groups", "--log-group-name-prefix", physical)
    return (any(g.get("logGroupName") == physical for g in (data or {}).get("logGroups", [])), "log group")


LIVE_CHECKS = {
    "AWS::ECS::Cluster": _ecs_cluster,
    "AWS::ECS::Service": _ecs_service,
    "AWS::ECS::TaskDefinition": lambda p: (aws("ecs", "describe-task-definition", "--task-definition", p) is not None, "task definition"),
    "AWS::ElasticLoadBalancingV2::LoadBalancer": lambda p: _described(("elbv2", "describe-load-balancers"), "LoadBalancers", "--load-balancer-arns", p),
    "AWS::ElasticLoadBalancingV2::TargetGroup": lambda p: _described(("elbv2", "describe-target-groups"), "TargetGroups", "--target-group-arns", p),
    "AWS::ElasticLoadBalancingV2::Listener": lambda p: _described(("elbv2", "describe-listeners"), "Listeners", "--listener-arns", p),
    "AWS::EC2::VPC": lambda p: _described(("ec2", "describe-vpcs"), "Vpcs", "--vpc-ids", p),
    "AWS::EC2::Subnet": lambda p: _described(("ec2", "describe-subnets"), "Subnets", "--subnet-ids", p),
    "AWS::EC2::SecurityGroup": lambda p: _described(("ec2", "describe-security-groups"), "SecurityGroups", "--group-ids", p),
    "AWS::EC2::InternetGateway": lambda p: _described(("ec2", "describe-internet-gateways"), "InternetGateways", "--internet-gateway-ids", p),
    "AWS::EC2::RouteTable": lambda p: _described(("ec2", "describe-route-tables"), "RouteTables", "--route-table-ids", p),
    "AWS::EC2::NatGateway": lambda p: _described(("ec2", "describe-nat-gateways"), "NatGateways", "--nat-gateway-ids", p),
    "AWS::IAM::Role": lambda p: (aws("iam", "get-role", "--role-name", p) is not None, "role"),
    "AWS::Logs::LogGroup": _log_group,
    "AWS::EFS::FileSystem": lambda p: _described(("efs", "describe-file-systems"), "FileSystems", "--file-system-id", p),
    "AWS::SecretsManager::Secret": lambda p: (aws("secretsmanager", "describe-secret", "--secret-id", p) is not None, "secret"),
    "AWS::SSM::Parameter": lambda p: (aws("ssm", "get-parameter", "--name", p) is not None, "parameter"),
    "AWS::S3::Bucket": lambda p: (aws("s3api", "head-bucket", "--bucket", p) is not None, "bucket"),
    "AWS::RDS::DBInstance": lambda p: _described(("rds", "describe-db-instances"), "DBInstances", "--db-instance-identifier", p),
    "AWS::KMS::Key": lambda p: (aws("kms", "describe-key", "--key-id", p) is not None, "key"),
}


def live_inventory(stack_resources):
    findings = []
    for resource in stack_resources:
        rtype = resource.get("ResourceType")
        physical = resource.get("PhysicalResourceId")
        check = LIVE_CHECKS.get(rtype)
        if check is None or not physical:
            findings.append({"logicalId": resource["LogicalResourceId"], "type": rtype, "live": "unchecked"})
            continue
        try:
            ok, detail = check(physical)
        except Exception as error:  # a failing probe is a finding, not a crash
            ok, detail = False, f"probe error: {error}"
        findings.append({"logicalId": resource["LogicalResourceId"], "type": rtype, "physicalId": physical,
                         "live": "found" if ok else "MISSING", "detail": detail})
    return findings


def compare(canonical, deployed, stack_resources, live):
    canon, dep = resources_of(canonical), resources_of(deployed)
    created = {r["LogicalResourceId"]: r for r in stack_resources}
    removed = sorted(r for r in canon if r not in dep)
    added = sorted(r for r in dep if r not in canon)
    retyped = sorted(r for r in canon if r in dep and canon[r] != dep[r])
    not_created = sorted(r for r in dep if r not in created)
    not_complete = sorted(r for r, v in created.items() if not str(v.get("ResourceStatus", "")).endswith("_COMPLETE"))
    missing_live = sorted(f["logicalId"] for f in live if f["live"] == "MISSING")
    by_type = {}
    for rid in removed:
        by_type.setdefault(canon[rid], {"removed": 0, "added": 0})["removed"] += 1
    for rid in added:
        by_type.setdefault(dep[rid], {"removed": 0, "added": 0})["added"] += 1
    return {
        "counts": {"canonical": len(canon), "deployed": len(dep), "created": len(created),
                   "liveFound": sum(1 for f in live if f["live"] == "found"),
                   "liveUnchecked": sum(1 for f in live if f["live"] == "unchecked"),
                   "liveMissing": len(missing_live)},
        "removedByAdapter": [{"logicalId": r, "type": canon[r]} for r in removed],
        "addedByAdapter": [{"logicalId": r, "type": dep[r]} for r in added],
        "retypedByAdapter": [{"logicalId": r, "from": canon[r], "to": dep[r]} for r in retyped],
        "notCreated": not_created,
        "createdNotComplete": not_complete,
        "missingFromEmulator": missing_live,
        "adapterChangesByType": by_type,
    }


def database_instances():
    """Engine, version and status of every database MiniStack currently holds."""
    described = (aws("rds", "describe-db-instances") or {}).get("DBInstances", [])
    return [f"{d.get('Engine')} {d.get('EngineVersion')} {d.get('DBInstanceStatus')}" for d in described]


def http_status(url):
    try:
        with urllib.request.urlopen(url, timeout=20) as response:
            return str(response.status)
    except urllib.error.HTTPError as error:
        return str(error.code)
    except Exception as error:  # connection refused, timeout, ...
        return type(error).__name__


def probe_health(deployed_stack, resources):
    """Waits for the application to answer on its health path (slow starters get HEALTH_WAIT seconds),
    reporting the load balancer's own verdict next to the real request."""
    groups = [r.get("PhysicalResourceId") for r in resources
              if r.get("ResourceType") == "AWS::ElasticLoadBalancingV2::TargetGroup"]
    if not groups:
        return {"health": "no-load-balancer", "healthPath": "", "healthHttp": ""}
    stacks = (aws("cloudformation", "describe-stacks", "--stack-name", deployed_stack) or {}).get("Stacks") or [{}]
    outputs = {o["OutputKey"]: o["OutputValue"] for o in stacks[0].get("Outputs", [])}
    url = outputs.get("MiniStackLocalUrl")
    deadline = time.time() + HEALTH_WAIT
    path, states, code = "/", [], ""
    while True:
        states = []
        for group in groups:
            described = (aws("elbv2", "describe-target-groups", "--target-group-arns", group) or {}).get("TargetGroups") or [{}]
            path = described[0].get("HealthCheckPath") or "/"
            health = aws("elbv2", "describe-target-health", "--target-group-arn", group) or {}
            states += [t["TargetHealth"]["State"] for t in health.get("TargetHealthDescriptions", [])]
        code = http_status(url.rstrip("/") + "/" + path.lstrip("/")) if url else ""
        answered = code.isdigit() and int(code) < 400
        if answered or time.time() > deadline:
            break
        time.sleep(10)
    verdict = ("no-targets" if not states else "healthy" if all(s == "healthy" for s in states)
               else "unhealthy:" + ",".join(sorted(set(states))))
    return {"health": verdict, "healthPath": path, "healthHttp": code}


def deploy_app(app, run_dir, preflight, option=None):
    app_id = app["applicationId"]
    app_dir = run_dir / (f"{app_id}@{option[0]}-{option[1]}" if option else app_id)
    app_dir.mkdir(parents=True, exist_ok=True)
    work = app_dir / "work"
    work.mkdir(exist_ok=True)
    context = context_for(app, option)
    stack = f"MS-{app_id}"
    deployed_stack = f"{stack}-ministack"
    context_path = app_dir / "context.json"
    context_path.write_text(json.dumps(context, indent=2))
    record = {"app": app_id if not option else f"{app_id} {option[0]} {option[1]}",
              "database": app["database"], "status": "?", "detail": ""}

    canonical, error = synthesize(context)
    if canonical is not None:
        (app_dir / "canonical.template.json").write_text(json.dumps(canonical, indent=2, sort_keys=True))
    else:
        record["canonicalError"] = error

    env = {"MINISTACK_PREFLIGHT": preflight}
    code, out = run_cli("deploy", context_path, work, DEPLOY_TIMEOUT, env)
    lines = json_lines(out)
    (app_dir / "deploy.json").write_text(json.dumps(lines, indent=2))
    final = next((l for l in reversed(lines) if l.get("phase") == "deploy"), {})
    record["cliExit"] = code
    if final.get("status") == "complete":
        record["status"] = "DEPLOYED"
    else:
        record["status"] = "BLOCKED" if "preflight" in out.lower() else "FAILED"
        record["detail"] = (final.get("message") or "")[:400].replace("\n", " | ")

    if record["status"] == "DEPLOYED":
        time.sleep(SETTLE_SECONDS)
        deployed = get_template(deployed_stack)
        resources = (aws("cloudformation", "list-stack-resources", "--stack-name", deployed_stack) or {}).get(
            "StackResourceSummaries", [])
        (app_dir / "deployed.template.json").write_text(json.dumps(deployed, indent=2, sort_keys=True))
        (app_dir / "stack-resources.json").write_text(json.dumps(resources, indent=2))
        live = live_inventory(resources)
        (app_dir / "live-inventory.json").write_text(json.dumps(live, indent=2))
        comparison = compare(canonical, deployed, resources, live)
        (app_dir / "comparison.json").write_text(json.dumps(comparison, indent=2))
        record.update(comparison["counts"])
        record["removedByAdapter"] = len(comparison["removedByAdapter"])
        record["addedByAdapter"] = len(comparison["addedByAdapter"])
        record["notCreated"] = len(comparison["notCreated"])
        record["createdNotComplete"] = len(comparison["createdNotComplete"])
        service = next((f for f in live if f["type"] == "AWS::ECS::Service"), None)
        record["ecsService"] = service.get("detail", "") if service else ""
        if option:
            record["databases"] = "; ".join(database_instances())
            record["health"] = "skipped"
        else:
            record.update(probe_health(deployed_stack, resources))
    elif canonical is not None:
        # Even without a deploy, show what the template would have needed.
        (app_dir / "comparison.json").write_text(json.dumps({"canonicalResources": resources_of(canonical)}, indent=2))

    # Always try to destroy, even after a failed deploy, so the next application starts clean.
    run_cli("destroy", context_path, work, DESTROY_TIMEOUT)
    leftover = (aws("cloudformation", "describe-stacks", "--stack-name", deployed_stack) or {}).get("Stacks", [])
    record["stackRemoved"] = not any(s.get("StackStatus") != "DELETE_COMPLETE" for s in leftover)
    subprocess.run("docker ps -a --format '{{.Names}}' | grep -E '^ministack-ecs-.*" + re.escape(stack) + "' | xargs -r docker rm -f",
                   shell=True, capture_output=True)
    shutil.rmtree(work, ignore_errors=True)
    (app_dir / "summary.json").write_text(json.dumps(record, indent=2))
    return record


def write_summary(run_dir, records, preflight):
    columns = ["app", "database", "status", "canonical", "deployed", "created", "removedByAdapter", "addedByAdapter",
               "notCreated", "createdNotComplete", "liveFound", "liveUnchecked", "liveMissing", "ecsService",
               "health", "healthPath", "healthHttp", "databases",
               "stackRemoved", "detail"]
    with open(run_dir / "summary.csv", "w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(records)
    counts = {}
    for r in records:
        counts[r["status"]] = counts.get(r["status"], 0) + 1
    lines = [f"# MiniStack application matrix ({datetime.datetime.now():%Y-%m-%d %H:%M})", "",
             f"Preflight: `{preflight}`. Applications: {len(records)}. " +
             ", ".join(f"{k}: {v}" for k, v in sorted(counts.items())), "",
             "| app | status | canon | deployed | created | adapter -/+ | live found/unchecked/missing | health | notes |",
             "|---|---|---|---|---|---|---|---|---|"]
    for r in records:
        lines.append("| {app} | {status} | {c} | {d} | {cr} | {rm}/{ad} | {lf}/{lu}/{lm} | {health} | {note} |".format(
            health=(r.get("health", "") + (f" ({r.get('healthPath')} → HTTP {r.get('healthHttp')})" if r.get("healthPath") else "")),
            app=r["app"], status=r["status"], c=r.get("canonical", ""), d=r.get("deployed", ""), cr=r.get("created", ""),
            rm=r.get("removedByAdapter", ""), ad=r.get("addedByAdapter", ""), lf=r.get("liveFound", ""),
            lu=r.get("liveUnchecked", ""), lm=r.get("liveMissing", ""),
            note=(r.get("detail") or (r.get("databases", "") + " | " if r.get("databases") else "") + r.get("ecsService", ""))[:140].replace("|", "/")))
    (run_dir / "summary.md").write_text("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apps", help="comma-separated application ids (default: all)")
    parser.add_argument("--inventory", default="/tmp/app-inventory.tsv", help="TSV from scripts/AppInventory.java")
    parser.add_argument("--preflight", default="enforce", choices=["enforce", "warn", "off"])
    parser.add_argument("--run", help="run directory name to create or resume (default: timestamp)")
    parser.add_argument("--db-versions", action="store_true",
                        help="deploy each database application once per engine/version it lists")
    parser.add_argument("--force", action="store_true", help="redo applications that already have a summary.json")
    args = parser.parse_args()

    apps = load_inventory(args.inventory)
    if args.apps:
        wanted = set(args.apps.split(","))
        apps = [a for a in apps if a["applicationId"] in wanted]
    run_dir = OUT_ROOT / (args.run or datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
    run_dir.mkdir(parents=True, exist_ok=True)

    jobs = []
    for app in apps:
        if not args.db_versions:
            jobs.append((app, None))
        elif app.get("database") in ("REQUIRED", "OPTIONAL") and app.get("databaseOptions"):
            for pair in app["databaseOptions"].split(","):
                engine, version = pair.split(":")
                jobs.append((app, (engine, version)))

    records = []
    for index, (app, option) in enumerate(jobs, 1):
        label = app["applicationId"] + (f"@{option[0]}-{option[1]}" if option else "")
        done = run_dir / label / "summary.json"
        if done.exists() and not args.force:
            records.append(json.loads(done.read_text()))
            print(f"[{index}/{len(jobs)}] {label}: skipped (already recorded)", flush=True)
            continue
        started = time.time()
        record = deploy_app(app, run_dir, args.preflight, option)
        records.append(record)
        print(f"[{index}/{len(jobs)}] {record['app']}: {record['status']} "
              f"(canon={record.get('canonical', '-')} deployed={record.get('deployed', '-')} "
              f"created={record.get('created', '-')} live-missing={record.get('liveMissing', '-')}) "
              f"{time.time() - started:.0f}s {record.get('detail', '')[:120]}", flush=True)
        write_summary(run_dir, records, args.preflight)
    write_summary(run_dir, records, args.preflight)
    print(f"\nSummary: {run_dir / 'summary.md'}")


if __name__ == "__main__":
    sys.exit(main())
