#!/usr/bin/env python3
"""
Compares, per compliance config (framework x security profile x runtime), what CDK synth says
should exist against what LocalStackTemplateAdapter actually produced for the real deploy --
entirely from files CDK synth and the adapter already wrote to cdk.out/, no live LocalStack
state or fresh AWS CLI calls required. Also folds in the real deploy PASS/FAIL result from
deploy-localstack-compliance-matrix.sh and, where present, real cdk-nag findings.

Usage: ./localstack-compliance-comparison.py
Reads (all from cdk.out/, written by CDK synth / LocalStackTemplateAdapter):
  <config>.template.json                    canonical synth output
  <config>.localstack.template.json          LocalStack-adapted template (missing if synth/adapt
                                              never completed, e.g. an ENFORCE-mode cfn-guard block)
  <config>.localstack-adaptations.json       exact adaptation records (path/reason/original)
  AwsSolutions-<config>-NagReport.json       cdk-nag findings, where captured
  scripts/validation-results/localstack-compliance-matrix-results.tsv   real deploy PASS/FAIL
Writes:
  scripts/validation-results/localstack-compliance-comparison.html
"""

import json
import csv
from collections import Counter
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CDK_OUT = ROOT / "cdk.out"
RESULTS_DIR = ROOT / "scripts" / "validation-results"
TSV_PATH = RESULTS_DIR / "localstack-compliance-matrix-results.tsv"
OUTPUT_PATH = RESULTS_DIR / "localstack-compliance-comparison.html"

CONFIGS = [
    (fw, profile, "fargate")
    for fw in ("soc2", "pcidss", "hipaa", "gdpr")
    for profile in ("dev", "staging", "production")
] + [("allframeworks", "production", "ec2")]

FRAMEWORK_LABELS = {"soc2": "SOC2", "pcidss": "PCI-DSS", "hipaa": "HIPAA", "gdpr": "GDPR",
                     "allframeworks": "SOC2+PCI-DSS+HIPAA+GDPR"}


def load_json(path: Path):
    return json.loads(path.read_text()) if path.exists() else None


def config_rule_identity(props: dict) -> str:
    """Config rules identify by an explicit ConfigRuleName (custom rules) or, for AWS-managed
    rules, Source.SourceIdentifier -- every AWS::Config::ConfigRule resource has exactly one."""
    name = props.get("ConfigRuleName")
    if name:
        return name
    return props.get("Source", {}).get("SourceIdentifier", "?")


def extract_resources(template: dict):
    resources = template.get("Resources", {})
    type_counts = Counter(r["Type"] for r in resources.values())
    config_rules = sorted({
        config_rule_identity(r.get("Properties", {}))
        for r in resources.values() if r["Type"] == "AWS::Config::ConfigRule"
    })
    return len(resources), type_counts, config_rules


def load_deploy_results():
    if not TSV_PATH.exists():
        return {}
    with open(TSV_PATH) as f:
        return {row["config"]: row for row in csv.DictReader(f, delimiter="\t")}


def load_nag_summary(ctx_name: str):
    data = load_json(CDK_OUT / f"AwsSolutions-{ctx_name}-NagReport.json")
    if data is None:
        return None
    lines = data.get("lines", [])
    counts = Counter(l["compliance"] for l in lines)
    non_compliant = [l for l in lines if l["compliance"] == "Non-Compliant"]
    return {"total": len(lines), "counts": dict(counts), "non_compliant": non_compliant}


def build_rows(deploy_results):
    rows = []
    for fw, profile, runtime in CONFIGS:
        ctx_name = f"CFCompliance-{fw}-{profile}-{runtime}"
        canonical = load_json(CDK_OUT / f"{ctx_name}.template.json")
        adapted = load_json(CDK_OUT / f"{ctx_name}.localstack.template.json")
        adaptations = load_json(CDK_OUT / f"{ctx_name}.localstack-adaptations.json") or []
        deploy = deploy_results.get(ctx_name)
        nag = load_nag_summary(ctx_name)

        row = {
            "config": ctx_name,
            "framework": FRAMEWORK_LABELS.get(fw, fw),
            "profile": profile.upper(),
            "runtime": runtime.upper(),
            "synthesized": canonical is not None,
            "adapted_exists": adapted is not None,
            "deploy_result": deploy["result"] if deploy else None,
            "stack_status": deploy["stack_status"] if deploy else None,
            "adaptation_count": len(adaptations),
            "adaptation_reasons": sorted({a["reason"] for a in adaptations}),
            "nag": nag,
        }

        if canonical is not None:
            total, types, config_rules = extract_resources(canonical)
            row["canonical_total"] = total
            row["canonical_config_rules"] = config_rules
        else:
            row["canonical_total"] = 0
            row["canonical_config_rules"] = []
            types = Counter()

        if adapted is not None:
            a_total, a_types, a_config_rules = extract_resources(adapted)
            row["adapted_total"] = a_total
            row["adapted_config_rules"] = a_config_rules
            stripped = {t: types[t] - a_types.get(t, 0) for t in types if types[t] > a_types.get(t, 0)}
            row["stripped_types"] = sorted(stripped.items(), key=lambda kv: -kv[1])
            row["config_rules_dropped"] = sorted(set(row["canonical_config_rules"]) - set(a_config_rules))
        else:
            row["adapted_total"] = None
            row["adapted_config_rules"] = []
            row["stripped_types"] = []
            row["config_rules_dropped"] = []

        rows.append(row)
    return rows


def render_html(rows) -> str:
    total = len(rows)
    synthesized = len([r for r in rows if r["synthesized"]])
    adapted_ok = len([r for r in rows if r["adapted_exists"]])
    real_pass = len([r for r in rows if r["deploy_result"] == "PASS"])
    config_rule_drops = sum(len(r["config_rules_dropped"]) for r in rows)

    def row_html(r):
        if not r["synthesized"]:
            synth_cell = '<span class="miss">not synthesized</span>'
        else:
            synth_cell = f"{r['canonical_total']} resources<br><small>{len(r['canonical_config_rules'])} Config rules</small>"

        if not r["adapted_exists"]:
            adapt_cell = '<span class="miss">no LocalStack template — synth/adapt never completed for this config (expected for ENFORCE-mode cfn-guard blocks)</span>'
        else:
            stripped = ", ".join(f"{t.split('::')[-1]} ×{c}" for t, c in r["stripped_types"]) or "none"
            adapt_cell = (
                f"{r['adapted_total']} resources<br>"
                f"<small>{r['adaptation_count']} adaptations applied</small><br>"
                f"<small>stripped: {stripped}</small>"
            )
            if r["config_rules_dropped"]:
                adapt_cell += f"<br><span class='miss'>Config rules lost in adapt: {', '.join(r['config_rules_dropped'])}</span>"

        if r["deploy_result"] == "PASS":
            deploy_cell = f'<span class="match">✅ PASS</span><br><small>{r["stack_status"]}</small>'
        elif r["deploy_result"] == "FAIL":
            deploy_cell = f'<span class="miss">❌ FAIL</span><br><small>{r["stack_status"]}</small>'
        else:
            deploy_cell = '<em>no deploy result recorded</em>'

        if r["nag"] is None:
            nag_cell = '<em>no cdk-nag report captured</em>'
        else:
            n = r["nag"]
            counts = ", ".join(f"{k}: {v}" for k, v in sorted(n["counts"].items()))
            nag_cell = f"{n['total']} findings<br><small>{counts}</small>"
            if n["non_compliant"]:
                nag_cell += f"<br><span class='miss'>{len(n['non_compliant'])} non-compliant</span>"

        flagged = bool(r["config_rules_dropped"]) or (r["synthesized"] and not r["adapted_exists"])
        return f"""
        <tr class="{'discrepancy' if flagged else ''}">
            <td>{r['framework']}<br><small>{r['profile']} / {r['runtime']}</small></td>
            <td>{synth_cell}</td>
            <td>{adapt_cell}</td>
            <td>{deploy_cell}</td>
            <td>{nag_cell}</td>
        </tr>"""

    rows_html = "\n".join(row_html(r) for r in rows)

    return f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>LocalStack Compliance: Synth vs Adapted vs Real Deploy</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 20px; }}
        .container {{ max-width: 1700px; margin: 0 auto; }}
        .header {{ text-align: center; color: white; padding: 30px 20px; }}
        .header h1 {{ font-size: 2.2em; margin-bottom: 10px; text-shadow: 2px 2px 4px rgba(0,0,0,0.2); }}
        .header p {{ opacity: 0.9; }}
        .back-link {{ display: inline-block; margin-bottom: 20px; color: white; text-decoration: none; font-weight: 500; padding: 10px 20px; background: rgba(255,255,255,0.2); border-radius: 6px; }}
        .content {{ background: white; border-radius: 12px; padding: 30px; box-shadow: 0 10px 30px rgba(0,0,0,0.2); margin-bottom: 30px; }}
        .stats {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 20px; margin: 20px 0 30px; }}
        .stat-card {{ background: #f8f9fa; padding: 20px; border-radius: 8px; text-align: center; border-left: 4px solid #667eea; }}
        .stat-card.success {{ border-left-color: #27ae60; }}
        .stat-card.failed {{ border-left-color: #e74c3c; }}
        .stat-number {{ font-size: 2.2em; font-weight: bold; color: #667eea; }}
        .stat-card.success .stat-number {{ color: #27ae60; }}
        .stat-card.failed .stat-number {{ color: #e74c3c; }}
        .stat-label {{ color: #7f8c8d; font-size: 0.9em; }}
        table {{ width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 0.9em; }}
        th {{ background: #2c3e50; color: white; padding: 10px; text-align: left; }}
        td {{ padding: 10px; border-bottom: 1px solid #eee; vertical-align: top; }}
        tr.discrepancy {{ background: #fff3cd; }}
        small {{ color: #7f8c8d; }}
        .match {{ color: #27ae60; font-weight: 600; }}
        .miss {{ color: #e74c3c; }}
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <h1>LocalStack Compliance: Synth vs Adapted vs Real Deploy</h1>
        <p>Per config: what CDK synth produced, what LocalStackTemplateAdapter changed to make it deployable, and the real deploy result — read directly from cdk.out, no live LocalStack query needed</p>
        <p><small>Generated {datetime.now().isoformat()}</small></p>
    </div>
    <a class="back-link" href="compliance-validation-dashboard.html">← Back to Compliance Dashboard</a>
    <div class="content">
        <div class="stats">
            <div class="stat-card"><div class="stat-number">{total}</div><div class="stat-label">Configs</div></div>
            <div class="stat-card {'success' if synthesized == total else 'failed'}"><div class="stat-number">{synthesized}/{total}</div><div class="stat-label">Synthesized</div></div>
            <div class="stat-card {'success' if adapted_ok == total else 'failed'}"><div class="stat-number">{adapted_ok}/{total}</div><div class="stat-label">LocalStack-adapted</div></div>
            <div class="stat-card success"><div class="stat-number">{real_pass}/{total}</div><div class="stat-label">Real deploys passed</div></div>
            <div class="stat-card {'failed' if config_rule_drops else 'success'}"><div class="stat-number">{config_rule_drops}</div><div class="stat-label">Config rules lost in adapt</div></div>
        </div>
        <table>
            <thead>
                <tr>
                    <th>Framework / Profile / Runtime</th>
                    <th>Synth (canonical template)</th>
                    <th>LocalStack adapt (what changed)</th>
                    <th>Real deploy</th>
                    <th>cdk-nag findings</th>
                </tr>
            </thead>
            <tbody>
{rows_html}
            </tbody>
        </table>
    </div>
</div>
</body>
</html>"""


def main():
    if not CDK_OUT.exists():
        print(f"No cdk.out directory at {CDK_OUT} -- run a synth (or the compliance sweep) first.")
        return 1
    deploy_results = load_deploy_results()
    if not deploy_results:
        print(f"Warning: no deploy results at {TSV_PATH} -- deploy column will be empty for every row.")
    rows = build_rows(deploy_results)
    OUTPUT_PATH.write_text(render_html(rows), encoding="utf-8")
    print(f"Wrote {OUTPUT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
