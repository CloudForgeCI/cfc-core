# Built-in Plugins

This page lists the application and compliance plugins registered by `cloudforge-api`, and the
sample plugins in `cfc-testing`. For how plugins are discovered, see
[PLUGIN-SYSTEM.md](PLUGIN-SYSTEM.md).

Validate each application specification against your workload before production use.

---

## Applications (35)

### General applications (16)

These implement `ApplicationSpec`, carry `@ApplicationPlugin`, and deploy with the
`application-service` topology. All of them declare support for both Fargate and EC2.

| Category | Application | ID | OIDC |
|----------|-------------|----|------|
| CI/CD | Jenkins | `jenkins` | yes |
| CI/CD | GitLab | `gitlab` | yes |
| CI/CD | Drone | `drone` | no |
| Code quality | SonarQube | `sonarqube` | no |
| Version control | Gitea | `gitea` | no |
| Monitoring | Grafana | `grafana` | yes |
| Monitoring | Prometheus | `prometheus` | no |
| Analytics | Metabase | `metabase` | yes |
| Analytics | Superset | `superset` | no |
| Database | PostgreSQL | `postgresql` | no |
| Database | Redis | `redis` | no |
| Artifact registry | Nexus | `nexus` | no |
| Artifact registry | Harbor | `harbor` | no |
| Secrets | Vault | `vault` | no |
| Collaboration | Mattermost Enterprise | `mattermost-enterprise` | yes |
| Collaboration | Mattermost Team | `mattermost-team` | yes |

The OIDC column reflects `supportsOidc` on the plugin annotation. Applications without
application-level OIDC can still use `alb-oidc` where the spec allows it.

### PHP / CMS applications (19)

These implement `CmsSpec`, which extends `ApplicationSpec` with PHP runtime, media storage,
CDN, object cache, and cron settings. They carry `@CmsPlugin` and deploy with
`"topology": "cms-service"` and the ID below as `applicationId`.

| Category | Application | ID |
|----------|-------------|----|
| CMS | WordPress | `wordpress` |
| CMS | Drupal | `drupal` |
| CMS | Joomla | `joomla` |
| CMS | TYPO3 | `typo3` |
| CMS | Concrete CMS | `concrete-cms` |
| CMS | October CMS | `october-cms` |
| E-commerce | WooCommerce | `woocommerce` |
| E-commerce | Magento 2 / Adobe Commerce | `magento` |
| E-commerce | PrestaShop | `prestashop` |
| E-commerce | OpenCart | `opencart` |
| E-commerce | Sylius | `sylius` |
| E-commerce | Bagisto | `bagisto` |
| Forum | phpBB | `phpbb` |
| Forum | Flarum | `flarum` |
| Forum | MyBB | `mybb` |
| CRM | SuiteCRM | `suitecrm` |
| Wiki | MediaWiki | `mediawiki` |
| LMS | Moodle | `moodle` |
| Social | UNA (Dolphin) | `dolphin-una` |

Depending on what each spec declares, the `cms-service` topology can add:

- an S3 media bucket and CloudFront distribution with media, static, and admin path behaviors
- an ElastiCache Redis object cache, with CMS-specific Redis and database environment variables
- system cron entries from `cronCommands(...)`
- PHP-FPM and OPcache settings from the spec

The [CMS guide](../applications/CMS.md) documents CMS deployment settings, and the
[application catalog](../applications/README.md) covers individual applications.

---

## Compliance frameworks (18)

### Always load (11)

These install for every deployment that has `auditManagerEnabled: true`.

| Framework ID | Priority | Scope |
|--------------|----------|-------|
| `KeyManagement` | -10 | KMS, secrets, and certificate controls |
| `DatabaseSecurity` | -5 | RDS and DynamoDB controls |
| `AdvancedMonitoring` | -5 | Security Hub, Inspector, and Macie |
| `ThreatProtection` | 0 | Malware protection, intrusion detection, file integrity |
| `IncidentResponse` | 0 | Backup, recovery, and forensics |
| `ComputeSecurity` | 0 | Compute resource controls |
| `LambdaSecurity` | 0 | Lambda function controls |
| `CdnApiSecurity` | 0 | CDN and API Gateway controls |
| `ElbSecurity` | 0 | Load balancer controls |
| `MessagingSecurity` | 0 | Messaging service controls |
| `IamSecurity` | 0 | IAM policy and access controls |

### Conditional (7)

These install when named in `complianceFrameworks`.

| Framework ID | Priority | Standard |
|--------------|----------|----------|
| `HIPAA` | 10 | HIPAA Security Rule technical safeguards |
| `HIPAA-Organizational` | 15 | HIPAA administrative safeguards |
| `PCI-DSS` | 20 | PCI DSS |
| `GDPR` | 30 | GDPR technical measures |
| `GDPR-Organizational` | 35 | GDPR organizational measures |
| `SOC2` | 40 | SOC 2 Trust Services Criteria |
| `ISO-27001` | 50 | ISO/IEC 27001:2022 |

`complianceFrameworks` currently accepts only `hipaa`, `pci-dss`, `soc2`, and `gdpr`; see the
[limitation](COMPLIANCE-PLUGIN-GUIDE.md#enabling-a-framework) in the compliance plugin guide.

```json
{
  "securityProfile": "PRODUCTION",
  "complianceFrameworks": "hipaa,pci-dss",
  "auditManagerEnabled": true
}
```

---

## Sample plugins

The [`cfc-testing`](https://github.com/CloudForgeCI/cfc-core/tree/develop/cfc-testing) module is the reference plugin consumer and is also
published as [cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample). It
registers:

| Class | Type | ID | Notes |
|-------|------|----|-------|
| `CraftCmsApplicationSpec` | `CmsSpec` + `DatabaseSpec` | `craft-cms` | Craft CMS; `web/` document root, queue cron, `alb-oidc` |
| `CustomSecurityPolicyRules` | `FrameworkRules` | `CustomSecurity` | Example internal policy (priority 60) |
| `OpenSourceSecurityPolicyRules` | `FrameworkRules` | `OpenSourceSecurity` | Example open-source project policy (priority 65) |

Use these as starting points for your own plugins. To build one, follow the
[application plugin guide](APPLICATION-PLUGIN-GUIDE.md) or the
[compliance plugin guide](COMPLIANCE-PLUGIN-GUIDE.md).
