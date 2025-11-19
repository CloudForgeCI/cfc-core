# PCI-DSS Application-Level Security Guide

## IMPORTANT: Scope

**This guide covers application controls only. Infrastructure + application controls ≠ full PCI-DSS compliance.**

You still need:
- Organizational policies (security, incident response, acceptable use)
- Procedures (change management, access reviews, vulnerability scans)
- Training programs (security awareness)
- Third-party assessment (Qualified Security Assessor validation)
- Documentation (risk assessments, network diagrams)

Infrastructure (CloudForge CI) + Application (this guide) + Organizational controls + QSA = Compliance

## Overview

This guide shows how to configure Jenkins application security for environments processing cardholder data. CloudForge CI handles infrastructure; this covers the Jenkins layer.

---

## Jenkins Security Hardening for PCI-DSS

### 1. Authentication & Authorization (Requirement 8)

#### Configure Matrix Authorization Strategy

```groovy
// In Jenkins Configuration as Code (JCasC)
jenkins:
  authorizationStrategy:
    projectMatrix:
      permissions:
        - "Overall/Administer:admin-group"
        - "Overall/Read:authenticated"
        - "Job/Build:build-group"
        - "Job/Read:view-group"
      grantedPermissions:
        - "Overall/Administer:admin"
```

#### Install Required Security Plugins

```bash
# Install via Jenkins CLI or Web UI
java -jar jenkins-cli.jar -s http://localhost:8080/ install-plugin \
  matrix-auth \
  ldap \
  oic-auth \
  credentials-binding \
  mask-passwords \
  audit-trail
```

#### Configure OIDC Authentication (SSO with MFA)

```groovy
// JCasC configuration for OIDC
jenkins:
  securityRealm:
    oic:
      clientId: "${OIDC_CLIENT_ID}"
      clientSecret: "${OIDC_CLIENT_SECRET}"
      tokenServerUrl: "https://auth.example.com/oauth2/token"
      authorizationServerUrl: "https://auth.example.com/oauth2/authorize"
      userInfoServerUrl: "https://auth.example.com/oauth2/userinfo"
      userNameField: "email"
      scopes: "openid profile email"
      fullNameFieldName: "name"
      emailFieldName: "email"
      groupsFieldName: "groups"
```

### 2. Credential Management (Requirement 3)

#### Never Store Plain Text Credentials

```groovy
// BAD - Never do this
def password = "myPassword123"

// GOOD - Use credentials binding
withCredentials([usernamePassword(
  credentialsId: 'my-credentials',
  usernameVariable: 'USERNAME',
  passwordVariable: 'PASSWORD'
)]) {
  sh '''
    echo "Authenticating as $USERNAME"
    # Password is masked in logs
  '''
}
```

#### Mask Sensitive Data in Logs

```groovy
// Use mask-passwords plugin
wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [
  [var: 'API_KEY', password: env.API_KEY],
  [var: 'SECRET', password: env.SECRET]
]]) {
  sh '''
    echo "API Key: $API_KEY"  # Will show: ********
  '''
}
```

#### Configure Credentials Plugin for PCI-DSS

```groovy
unclassified:
  globalConfigFiles:
    configs:
      - maven:
          id: "maven-settings"
          name: "Maven Settings (PCI-compliant)"
          comment: "Uses credential binding, no plain text"
          content: |
            <settings>
              <servers>
                <server>
                  <id>nexus</id>
                  <username>${env.NEXUS_USERNAME}</username>
                  <password>${env.NEXUS_PASSWORD}</password>
                </server>
              </servers>
            </settings>
```

### 3. Data Protection - Cardholder Data (Requirement 3)

#### Identify Cardholder Data Locations

Common places where card data might appear:
1. **Build logs**: Payment processing test transactions
2. **Artifacts**: Database dumps, test data files
3. **Configuration files**: API keys for payment gateways
4. **Environment variables**: Merchant IDs, gateway credentials

#### Prevent Cardholder Data in Logs

```groovy
// Jenkins Pipeline - Sanitize logs
def sanitizeLog(String output) {
  // Mask credit card numbers (PAN)
  output = output.replaceAll(
    /\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b/,
    '****-****-****-****'
  )

  // Mask CVV
  output = output.replaceAll(/\bCVV:\s*\d{3,4}\b/, 'CVV:***')

  return output
}

pipeline {
  stages {
    stage('Test Payment Gateway') {
      steps {
        script {
          def result = sh(returnStdout: true, script: 'curl payment-api')
          def sanitized = sanitizeLog(result)
          echo sanitized
        }
      }
    }
  }
}
```

#### Configure Artifact Retention

```groovy
// In Jenkinsfile
properties([
  buildDiscarder(
    logRotator(
      daysToKeepStr: '90',      // PCI-DSS: Keep logs 90 days
      numToKeepStr: '100',
      artifactDaysToKeepStr: '30',
      artifactNumToKeepStr: '10'
    )
  )
])
```

#### Encrypt Artifacts Containing Sensitive Data

```groovy
// Encrypt artifacts before upload to S3
sh '''
  # Encrypt database dump
  gpg --symmetric --cipher-algo AES256 \
    --output backup.sql.gpg backup.sql

  # Upload encrypted file
  aws s3 cp backup.sql.gpg s3://artifacts-bucket/ \
    --sse AES256

  # Delete unencrypted original
  shred -u backup.sql
'''
```

### 4. Audit Logging (Requirement 10)

#### Configure Audit Trail Plugin

```groovy
// JCasC - Audit Trail Plugin
unclassified:
  auditTrail:
    loggers:
      - logFile:
          log: "/var/log/jenkins/audit.log"
          limit: 1000
          count: 10
      - syslog:
          serverHostname: "syslog.example.com"
          serverPort: 514
          protocol: "UDP"
    pattern: |
      .*/(?:configSubmit|doDelete|postBuildResult|enable|disable|cancelQueue|stop|toggleLogKeep|doWipeOutWorkspace|createItem|createView|toggleOffline|cancelQuietDown|quietDown|restart|exit|safeExit)
```

#### Log Security Events to CloudWatch

```groovy
// Jenkins Pipeline - Send audit events
def logSecurityEvent(String event, String details) {
  sh """
    aws logs put-log-events \
      --log-group-name /jenkins/security-events \
      --log-stream-name \$(hostname) \
      --log-events timestamp=\$(date +%s000),message='${event}: ${details}'
  """
}

// Usage in pipeline
stage('Deploy to Production') {
  steps {
    script {
      logSecurityEvent('DEPLOYMENT', "User ${env.BUILD_USER} deploying to production")
      // deployment steps
    }
  }
}
```

### 5. Secure Build Pipelines (Requirement 6)

#### Code Review Requirements

```groovy
// Require code review before production deployment
stage('Validate Code Review') {
  when {
    branch 'main'
  }
  steps {
    script {
      def prNumber = sh(
        returnStdout: true,
        script: 'git log -1 --pretty=%B | grep -oP "\\(#\\K[0-9]+"'
      ).trim()

      if (!prNumber) {
        error("Production deployment requires pull request with code review")
      }

      def approved = sh(
        returnStdout: true,
        script: "gh pr view ${prNumber} --json reviewDecision -q .reviewDecision"
      ).trim()

      if (approved != 'APPROVED') {
        error("Pull request #${prNumber} must be approved before production deployment")
      }
    }
  }
}
```

#### Security Scanning in Pipeline

```groovy
pipeline {
  agent any

  stages {
    stage('SAST - Static Analysis') {
      steps {
        // SonarQube scan
        withSonarQubeEnv('SonarQube') {
          sh 'mvn sonar:sonar'
        }

        // Wait for quality gate
        timeout(time: 10, unit: 'MINUTES') {
          waitForQualityGate abortPipeline: true
        }
      }
    }

    stage('Dependency Check') {
      steps {
        // OWASP Dependency Check
        sh 'mvn org.owasp:dependency-check-maven:check'

        // Fail if high/critical vulnerabilities
        dependencyCheckPublisher(
          pattern: '**/dependency-check-report.xml',
          failedTotalHigh: 0,
          failedTotalCritical: 0
        )
      }
    }

    stage('Secret Scanning') {
      steps {
        // Scan for secrets in code
        sh '''
          # Install truffleHog if not present
          pip3 install truffleHog

          # Scan repository
          truffleHog --regex --entropy=True .
        '''
      }
    }

    stage('Container Scanning') {
      steps {
        // Trivy container scanning
        sh '''
          trivy image --severity HIGH,CRITICAL \
            --exit-code 1 \
            myapp:${BUILD_NUMBER}
        '''
      }
    }
  }
}
```

### 6. Change Control (Requirement 6.4)

#### Enforce Change Control Process

```groovy
// Require change ticket for production
stage('Validate Change Control') {
  when {
    environment name: 'DEPLOY_ENV', value: 'production'
  }
  steps {
    script {
      def changeTicket = input(
        message: 'Enter Change Control Ticket Number',
        parameters: [
          string(name: 'TICKET', description: 'Ticket number (e.g., CHG0012345)')
        ]
      )

      // Validate ticket exists and is approved
      sh """
        # Call change management API
        TICKET_STATUS=\$(curl -s https://change-api.example.com/tickets/${changeTicket} | jq -r .status)

        if [ "\$TICKET_STATUS" != "approved" ]; then
          echo "Change ticket ${changeTicket} is not approved"
          exit 1
        fi
      """

      // Log the change
      logSecurityEvent('CHANGE_CONTROL', "Deployment authorized by ticket ${changeTicket}")
    }
  }
}
```

### 7. Test Data Management (Requirement 3.1)

#### Never Use Real Cardholder Data in Tests

```groovy
// Jenkins Pipeline - Generate test data
stage('Prepare Test Data') {
  steps {
    sh '''
      # Generate synthetic test credit cards
      # These are valid Luhn algorithm but not real cards

      cat > test-cards.json << 'EOF'
      {
        "test_cards": [
          {
            "number": "4111111111111111",
            "cvv": "123",
            "expiry": "12/25",
            "type": "visa",
            "note": "Test card - always approved"
          },
          {
            "number": "5555555555554444",
            "cvv": "456",
            "expiry": "12/25",
            "type": "mastercard",
            "note": "Test card - always approved"
          }
        ]
      }
      EOF

      # These are well-known test cards from payment processors
      # They never represent real accounts
    '''
  }
}
```

#### Data Masking for Test Databases

```groovy
// Mask production data for test environments
stage('Sanitize Test Database') {
  steps {
    sh '''
      # Connect to test database and mask PII/cardholder data
      psql -h test-db -U admin -d myapp << 'SQL'

      -- Mask credit card numbers
      UPDATE payment_methods
      SET card_number = '****-****-****-' || RIGHT(card_number, 4),
          cvv = '***';

      -- Mask customer PII
      UPDATE customers
      SET email = CONCAT('test', customer_id, '@example.com'),
          phone = CONCAT('555-', LPAD(customer_id::TEXT, 7, '0'));

      -- Remove real names
      UPDATE customers
      SET first_name = 'Test',
          last_name = CONCAT('Customer', customer_id);

      SQL
    '''
  }
}
```

### 8. Access Reviews (Requirement 7.2.3)

#### Automate Quarterly Access Reviews

```groovy
// Jenkins job to generate access review report
pipeline {
  triggers {
    // Run quarterly on the 1st day of Jan, Apr, Jul, Oct
    cron('0 0 1 1,4,7,10 *')
  }

  stages {
    stage('Generate Access Report') {
      steps {
        script {
          // Get all Jenkins users
          def users = Jenkins.instance.securityRealm.allUsers

          def report = "# Jenkins Access Review - ${new Date()}\n\n"
          report += "| Username | Groups | Last Login | Has Admin | Action Required |\n"
          report += "|----------|--------|------------|-----------|------------------|\n"

          users.each { user ->
            def lastLogin = user.getProperty(hudson.security.UserProperty)?.lastLogin
            def groups = user.authorities.join(', ')
            def isAdmin = user.authorities.contains('admin')
            def daysSinceLogin = lastLogin ?
              ((new Date().time - lastLogin.time) / 86400000).toInteger() : 'Never'

            def action = daysSinceLogin > 90 ? 'Review - No recent activity' : 'OK'

            report += "| ${user.id} | ${groups} | ${daysSinceLogin} days | ${isAdmin} | ${action} |\n"
          }

          // Save report
          writeFile file: 'access-review.md', text: report

          // Email to security team
          emailext(
            to: 'security-team@example.com',
            subject: 'Quarterly Jenkins Access Review Required',
            body: report,
            mimeType: 'text/html'
          )
        }
      }
    }
  }
}
```

### 9. Vulnerability Management (Requirement 6.2)

#### Automate Jenkins Updates

```groovy
// Jenkins job to check for security updates
pipeline {
  triggers {
    cron('0 2 * * 1')  // Weekly on Monday at 2 AM
  }

  stages {
    stage('Check for Updates') {
      steps {
        script {
          def updateCenter = Jenkins.instance.updateCenter
          updateCenter.updateAllSites()

          def securityUpdates = []
          updateCenter.getPlugin('').updates.each { update ->
            if (update.hasSecurityFix()) {
              securityUpdates.add([
                name: update.plugin.name,
                currentVersion: update.plugin.version,
                newVersion: update.version,
                url: update.url
              ])
            }
          }

          if (securityUpdates.size() > 0) {
            def message = "Security updates available:\n\n"
            securityUpdates.each { update ->
              message += "- ${update.name}: ${update.currentVersion} -> ${update.newVersion}\n"
            }

            // Create Jira ticket for security team
            sh """
              curl -X POST https://jira.example.com/rest/api/2/issue \
                -H 'Content-Type: application/json' \
                -d '{
                  "fields": {
                    "project": {"key": "SEC"},
                    "summary": "Jenkins Security Updates Available",
                    "description": "${message}",
                    "issuetype": {"name": "Task"},
                    "priority": {"name": "High"}
                  }
                }'
            """
          }
        }
      }
    }
  }
}
```

### 10. Monitoring and Alerting (Requirement 10.6)

#### Daily Security Log Review

```groovy
// Jenkins job for daily security log review
pipeline {
  triggers {
    cron('0 9 * * *')  // Daily at 9 AM
  }

  stages {
    stage('Analyze Security Logs') {
      steps {
        script {
          // Query CloudWatch Logs for security events
          sh '''
            aws logs filter-log-events \
              --log-group-name /jenkins/security-events \
              --start-time $(($(date +%s) - 86400))000 \
              --filter-pattern "FAILED_LOGIN" \
              > failed-logins.json

            # Count failed logins
            FAILED_COUNT=$(jq '.events | length' failed-logins.json)

            if [ "$FAILED_COUNT" -gt 10 ]; then
              echo "WARNING: $FAILED_COUNT failed login attempts in last 24 hours"

              # Send alert
              aws sns publish \
                --topic-arn arn:aws:sns:us-east-1:123456789012:security-alerts \
                --subject "High Number of Failed Jenkins Logins" \
                --message "Detected $FAILED_COUNT failed login attempts. Review required."
            fi
          '''
        }
      }
    }
  }
}
```

---

## Jenkins Configuration as Code (JCasC) - PCI-DSS Template

Complete JCasC configuration for PCI-DSS compliance:

```yaml
jenkins:
  systemMessage: "This Jenkins instance processes cardholder data. PCI-DSS controls enforced."

  securityRealm:
    oic:
      clientId: "${OIDC_CLIENT_ID}"
      clientSecret: "${OIDC_CLIENT_SECRET}"
      tokenServerUrl: "${OIDC_TOKEN_URL}"
      authorizationServerUrl: "${OIDC_AUTH_URL}"
      userInfoServerUrl: "${OIDC_USERINFO_URL}"
      scopes: "openid profile email"

  authorizationStrategy:
    projectMatrix:
      permissions:
        - "Overall/Administer:admin-group"
        - "Overall/Read:authenticated"
        - "Job/Build:developer-group"
        - "Job/Configure:devops-group"
        - "Job/Read:viewer-group"

  disabledAdministrativeMonitors:
    - "hudson.diagnosis.ReverseProxySetupMonitor"

  crumbIssuer:
    standard:
      excludeClientIPFromCrumb: false

  remotingSecurity:
    enabled: true

security:
  globaljobdslsecurityconfiguration:
    useScriptSecurity: true

  scriptApproval:
    approvedSignatures:
      - "method java.lang.String trim"

unclassified:
  location:
    url: "https://jenkins-prod.example.com/"
    adminAddress: "jenkins-admin@example.com"

  auditTrail:
    loggers:
      - logFile:
          log: "/var/log/jenkins/audit.log"
          limit: 10000
          count: 52  # Keep 1 year of weekly rotated logs

  globalLibraries:
    libraries:
      - name: "pci-dss-pipeline-lib"
        defaultVersion: "main"
        retriever:
          modernSCM:
            scm:
              git:
                remote: "https://github.com/yourorg/jenkins-pipeline-library.git"
                credentialsId: "github-readonly"

credentials:
  system:
    domainCredentials:
      - credentials:
          # Use AWS Secrets Manager for credential storage
          - awsCredentialsImpl:
              scope: GLOBAL
              id: "aws-credentials"
              description: "AWS credentials from IAM role"

jobs:
  - script: >
      folder('pci-dss-compliance') {
        description('PCI-DSS compliance and security jobs')
      }

  - script: >
      pipelineJob('pci-dss-compliance/access-review') {
        description('Quarterly access review for PCI-DSS compliance')
        triggers {
          cron('0 0 1 1,4,7,10 *')
        }
        definition {
          cpsScm {
            scm {
              git {
                remote {
                  url('https://github.com/yourorg/jenkins-jobs.git')
                }
                branch('*/main')
              }
            }
            scriptPath('pci-dss/access-review.jenkinsfile')
          }
        }
      }
```

---

## PCI-DSS Application Control Checklist

### Initial Setup
- [ ] Install required security plugins (matrix-auth, oic-auth, audit-trail, mask-passwords)
- [ ] Configure OIDC authentication with MFA-enabled identity provider
- [ ] Set up matrix authorization with least privilege
- [ ] Enable CSRF protection
- [ ] Configure audit trail logging
- [ ] Disable script console for non-admins
- [ ] Enable agent-to-controller security

### Data Protection
- [ ] Implement credential binding for all secrets
- [ ] Configure log masking for sensitive data
- [ ] Set up artifact retention policies (90 days for logs)
- [ ] Encrypt artifacts containing sensitive data
- [ ] Never use real cardholder data in test environments
- [ ] Implement data sanitization for test databases

### Security Scanning
- [ ] Integrate SAST tools (SonarQube)
- [ ] Implement dependency scanning (OWASP Dependency Check)
- [ ] Add container scanning (Trivy/Clair)
- [ ] Enable secret scanning (TruffleHog)
- [ ] Fail builds on high/critical vulnerabilities

### Change Control
- [ ] Require pull request reviews for production code
- [ ] Enforce change control tickets for production deployments
- [ ] Implement approval gates in pipelines
- [ ] Log all configuration changes

### Monitoring & Auditing
- [ ] Send security events to CloudWatch Logs
- [ ] Configure alerts for failed logins
- [ ] Set up daily log review automation
- [ ] Generate quarterly access review reports
- [ ] Monitor for security plugin updates

### Ongoing Compliance
- [ ] Weekly vulnerability scanning
- [ ] Monthly plugin updates
- [ ] Quarterly access reviews
- [ ] Annual penetration testing
- [ ] Security awareness training

---

## Frequently Asked Questions

**Q: Can I use Jenkins local database for user management?**
A: No. PCI-DSS Requirement 8.3 requires multi-factor authentication. Use AWS SSO with MFA or another OIDC provider.

**Q: How do I handle payment gateway API keys?**
A: Store in AWS Secrets Manager, reference via IAM role, use credential binding in pipelines. Never commit to Git.

**Q: Can I use freestyle jobs?**
A: Pipeline as Code is recommended for audit trails and version control. If using freestyle, ensure all configurations are tracked.

**Q: What if I need to debug production issues?**
A: All debugging must be logged. Use read-only access when possible. Never disable security controls to debug.

**Q: How do I test payment integrations without real card data?**
A: Use test card numbers provided by payment processors (Stripe, PayPal test mode). These pass Luhn validation but are not real accounts.

---

## Additional Resources

- [Jenkins Security Best Practices](https://www.jenkins.io/doc/book/security/)
- [OWASP Jenkins Hardening Guide](https://owasp.org/www-project-jenkins-hardening/)
- [AWS Secrets Manager Integration](https://plugins.jenkins.io/aws-secrets-manager-credentials-provider/)
- [Jenkins Configuration as Code](https://github.com/jenkinsci/configuration-as-code-plugin)

---

**Remember**: Infrastructure + application controls = technical foundation only. PCI-DSS compliance requires organizational policies, procedures, and QSA validation.
