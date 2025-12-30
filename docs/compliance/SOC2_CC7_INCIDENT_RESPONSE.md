# SOC2 CC7.4/7.5 - Incident Response Procedures

**Control**: CC7.4 (Incident Response), CC7.5 (Incident Recovery)
**Status**: Fully Documented
**Last Updated**: 2025-12-16
**Owner**: Security Operations

---

## Overview

This document defines incident response and recovery procedures for organizations using CloudForge CI. These procedures ensure timely detection, response, and recovery from security incidents. These procedures satisfy SOC2 CC7.4 (Response to Identified Security Incidents) and CC7.5 (Recovery from Identified Security Incidents) requirements.

---

## CC7.4 - Incident Response

### Incident Classification

| Severity | Definition | Examples | Response Time |
|----------|------------|----------|---------------|
| **P1 - Critical** | Active breach, data exfiltration, complete service outage | Ransomware, confirmed data breach, production down | 15 minutes |
| **P2 - High** | Significant security event, partial outage | Unauthorized access attempt, DDoS attack, component failure | 1 hour |
| **P3 - Medium** | Security anomaly, degraded performance | Suspicious activity, increased errors, slow response | 4 hours |
| **P4 - Low** | Minor security event, no immediate impact | Failed login attempts, minor misconfiguration | 24 hours |

### Incident Response Team

| Role | Responsibilities | Contact Method |
|------|------------------|----------------|
| **Incident Commander** | Overall coordination, decisions, communication | PagerDuty |
| **Security Lead** | Threat analysis, containment strategy | PagerDuty |
| **Technical Lead** | System investigation, remediation | PagerDuty |
| **Communications Lead** | Stakeholder updates, external communication | Email/Phone |
| **Scribe** | Documentation, timeline maintenance | Slack |

### Incident Response Phases

```
┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│   Detect    │──>│   Triage    │──>│   Contain   │──>│  Eradicate  │
│             │   │             │   │             │   │             │
└─────────────┘   └─────────────┘   └─────────────┘   └─────────────┘
                                                              │
┌─────────────┐   ┌─────────────┐   ┌─────────────┐           │
│   Lessons   │<──│   Close     │<──│   Recover   │<──────────┘
│   Learned   │   │             │   │             │
└─────────────┘   └─────────────┘   └─────────────┘
```

---

### Phase 1: Detection

#### CloudForge CI Detection Sources

| Source | Event Types | Alert Destination |
|--------|-------------|-------------------|
| **GuardDuty** | Threat detection, anomalies | SNS -> PagerDuty |
| **Security Hub** | Aggregated findings | SNS -> Email |
| **CloudTrail** | API anomalies | CloudWatch Alarms -> SNS |
| **CloudWatch** | Performance/availability | SNS -> PagerDuty |
| **AWS Config** | Compliance violations | SNS -> Email |
| **WAF** | Web attacks | CloudWatch -> SNS |

#### Detection Procedures

1. **Automated Detection**
   - GuardDuty finding triggers SNS notification
   - PagerDuty creates incident and pages on-call
   - Security team acknowledges within SLA

2. **Manual Detection**
   - Staff reports suspicious activity via ticket
   - Security team triages within 4 hours
   - Escalate if confirmed incident

---

### Phase 2: Triage

#### Triage Checklist

```
INCIDENT TRIAGE
===============
Incident ID: [Auto-generated]
Detected: [Timestamp]
Source: [Detection source]

INITIAL ASSESSMENT
------------------
[ ] Confirm incident is real (not false positive)
[ ] Determine affected systems
[ ] Assess data at risk
[ ] Classify severity (P1/P2/P3/P4)
[ ] Identify attack vector (if applicable)

AFFECTED SYSTEMS
----------------
- [ ] Compute (ECS/EC2/Lambda)
- [ ] Database (RDS/DynamoDB)
- [ ] Storage (S3/EFS)
- [ ] Network (VPC/ALB/CloudFront)
- [ ] Identity (IAM/Cognito)

DATA IMPACT
-----------
- [ ] PII affected
- [ ] Financial data affected
- [ ] Credentials affected
- [ ] Customer data affected

SEVERITY: [P1/P2/P3/P4]
```

---

### Phase 3: Containment

#### Containment Actions by Incident Type

| Incident Type | Immediate Actions | CloudForge CI Tools |
|---------------|-------------------|---------------------|
| **Unauthorized Access** | Disable compromised credentials, block IP | IAM, Security Groups, WAF |
| **Malware/Ransomware** | Isolate affected instances, snapshot volumes | Security Groups, EBS Snapshots |
| **Data Exfiltration** | Block egress, revoke access | NACLs, IAM, S3 policies |
| **DDoS Attack** | Enable Shield, adjust WAF rules | AWS Shield, WAF |
| **Misconfiguration** | Revert to known-good config | CloudFormation rollback |

#### Emergency Containment Commands

```bash
# Isolate EC2 instance (replace security groups with isolation-only group)
aws ec2 modify-instance-attribute \
  --instance-id i-0123456789abcdef0 \
  --groups sg-0123456789abcdef0

# Disable IAM user console access
aws iam delete-login-profile \
  --user-name compromised-user

# Deactivate IAM user access keys
aws iam update-access-key \
  --user-name compromised-user \
  --access-key-id AKIAIOSFODNN7EXAMPLE \
  --status Inactive

# Block IP in WAF (requires lock-token from get-ip-set)
LOCK_TOKEN=$(aws wafv2 get-ip-set \
  --name BlockedIPs \
  --scope REGIONAL \
  --id a1b2c3d4-5678-90ab-cdef-EXAMPLE11111 \
  --query 'LockToken' --output text)

aws wafv2 update-ip-set \
  --name BlockedIPs \
  --scope REGIONAL \
  --id a1b2c3d4-5678-90ab-cdef-EXAMPLE11111 \
  --addresses "1.2.3.4/32" "10.0.0.0/8" \
  --lock-token "$LOCK_TOKEN"

# Revoke all active sessions for IAM role (inline deny-all policy)
aws iam put-role-policy \
  --role-name compromised-role \
  --policy-name DenyAllAccess \
  --policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Deny","Action":"*","Resource":"*"}]}'
```

---

### Phase 4: Eradication

#### Eradication Procedures

| Incident Type | Eradication Steps |
|---------------|-------------------|
| **Compromised Credentials** | 1. Rotate all affected credentials<br/>2. Review CloudTrail for actions taken<br/>3. Revert unauthorized changes |
| **Malware** | 1. Terminate affected instances<br/>2. Deploy fresh instances from known-good AMI<br/>3. Scan all connected systems |
| **Vulnerability Exploit** | 1. Patch vulnerable systems<br/>2. Update WAF rules<br/>3. Scan for indicators of compromise |
| **Data Breach** | 1. Close access vector<br/>2. Assess data accessed<br/>3. Preserve evidence for forensics |

---

## CC7.5 - Incident Recovery

### Recovery Procedures

#### Recovery Time Objectives

| System Tier | RTO | RPO | Recovery Method |
|-------------|-----|-----|-----------------|
| **Tier 1 (Critical)** | 1 hour | 15 minutes | Automated failover, hot standby |
| **Tier 2 (Important)** | 4 hours | 1 hour | Backup restore, CloudFormation redeploy |
| **Tier 3 (Standard)** | 24 hours | 24 hours | Backup restore |

#### CloudForge CI Recovery Capabilities

| Component | Recovery Method | Recovery Time |
|-----------|-----------------|---------------|
| **ECS Services** | Auto-healing, task replacement | Minutes |
| **RDS Database** | Point-in-time recovery, read replica promotion | 15-60 minutes |
| **S3 Data** | Versioning, cross-region replication | Minutes |
| **EFS** | AWS Backup restore | 1-4 hours |
| **Infrastructure** | CloudFormation stack redeploy | 30-60 minutes |

#### Recovery Verification Checklist

```
RECOVERY VERIFICATION
=====================
Incident ID: [ID]
Recovery Start: [Timestamp]
Recovery End: [Timestamp]

SYSTEM VERIFICATION
-------------------
[ ] All services operational
[ ] Health checks passing
[ ] No error spikes in logs
[ ] Performance metrics normal
[ ] Security controls active

DATA VERIFICATION
-----------------
[ ] Data integrity confirmed
[ ] No data loss identified
[ ] Backup systems operational
[ ] Replication functioning

SECURITY VERIFICATION
---------------------
[ ] Vulnerability remediated
[ ] No indicators of compromise
[ ] Access controls verified
[ ] Monitoring restored
[ ] GuardDuty/Security Hub clear

SIGN-OFF
--------
Technical Lead: _____________ Date: _____
Security Lead:  _____________ Date: _____
```

---

### Phase 5: Closure

#### Incident Closure Criteria

- [ ] Root cause identified
- [ ] All affected systems recovered
- [ ] Security controls verified
- [ ] No ongoing threat indicators
- [ ] Stakeholders notified
- [ ] Documentation complete

---

### Phase 6: Lessons Learned

#### Post-Incident Review Template

```
POST-INCIDENT REVIEW
====================
Incident ID: [ID]
Date: [Date]
Attendees: [Names]

INCIDENT SUMMARY
----------------
- What happened: [Description]
- Detection time: [Timestamp]
- Resolution time: [Timestamp]
- Total duration: [Duration]
- Severity: [P1/P2/P3/P4]

TIMELINE
--------
[Chronological list of events]

ROOT CAUSE
----------
[Root cause analysis]

WHAT WENT WELL
--------------
1. [Positive aspect]
2. [Positive aspect]

WHAT COULD BE IMPROVED
----------------------
1. [Improvement area]
2. [Improvement area]

ACTION ITEMS
------------
| Action | Owner | Due Date | Status |
|--------|-------|----------|--------|
| [Action] | [Name] | [Date] | Open |

METRICS
-------
- Time to detect: [Duration]
- Time to contain: [Duration]
- Time to eradicate: [Duration]
- Time to recover: [Duration]
```

---

## Communication Templates

### Internal Escalation Template

```
SECURITY INCIDENT ESCALATION
============================
Severity: [P1/P2/P3/P4]
Time: [Timestamp]

SUMMARY: [One-line description]

IMPACT:
- Systems affected: [List]
- Users affected: [Number/scope]
- Data at risk: [Yes/No - type if yes]

CURRENT STATUS: [Investigating/Containing/Eradicating/Recovering]

ACTIONS TAKEN:
1. [Action]
2. [Action]

NEXT STEPS:
1. [Planned action]

BRIDGE: [Conference call details]
```

### Customer Notification Template

```
SECURITY INCIDENT NOTIFICATION
==============================
Date: [Date]

Dear [Customer],

We are writing to inform you of a security incident that may affect your data.

WHAT HAPPENED
[Description of the incident]

WHAT INFORMATION WAS INVOLVED
[Types of data potentially affected]

WHAT WE ARE DOING
[Actions taken and planned]

WHAT YOU CAN DO
[Recommended customer actions]

FOR MORE INFORMATION
[Contact details]

We sincerely apologize for any inconvenience and remain committed to protecting your information.

[Signature]
```

---

## Incident Response Testing

| Test Type | Frequency | Scope |
|-----------|-----------|-------|
| **Tabletop Exercise** | Quarterly | Walk through scenarios |
| **Technical Drill** | Semi-annually | Execute containment procedures |
| **Full Simulation** | Annually | End-to-end incident response |
| **Recovery Test** | Quarterly | Backup restoration, failover |

---

## Audit Checklist

- [ ] Incident response plan documented and approved
- [ ] Incident response team identified with contact information
- [ ] Detection mechanisms operational (GuardDuty, Security Hub, CloudWatch)
- [ ] Containment procedures documented per incident type
- [ ] Recovery procedures tested quarterly
- [ ] Post-incident reviews conducted for all P1/P2 incidents
- [ ] Incident response exercises conducted annually
- [ ] Communication templates approved by legal
- [ ] Incident log maintained with all incidents
