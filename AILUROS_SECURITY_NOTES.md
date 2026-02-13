# Ailuros Control - Security Considerations

## ⚠️ Critical Security Notes for Production Deployment

Ailuros Control captures and stores **full request and response text** from LLM API calls. This data may contain:

- **Personally Identifiable Information (PII)**
- **Confidential business data**
- **API keys or credentials** (if accidentally included in prompts)
- **Proprietary algorithms** or trade secrets
- **Customer data** subject to GDPR, CCPA, HIPAA, etc.

**This document outlines security measures you MUST implement before production use.**

---

## Table of Contents

1. [Data Classification](#data-classification)
2. [PII and Sensitive Data](#pii-and-sensitive-data)
3. [Access Control](#access-control)
4. [Data Retention](#data-retention)
5. [Encryption](#encryption)
6. [Compliance](#compliance)
7. [Incident Response](#incident-response)
8. [Security Checklist](#security-checklist)

---

## Data Classification

### What Ailuros Stores

| Data Type | Table | Sensitivity | Notes |
|-----------|-------|-------------|-------|
| LLM Request Text | `ac_call.request_text` | **HIGH** | May contain PII, business secrets |
| LLM Response Text | `ac_call.response_text` | **HIGH** | May contain generated PII, confidential outputs |
| Model Parameters | `ac_call.model`, `temperature`, etc. | LOW | Metadata only |
| Cost Estimates | `ac_call.cost_estimate_usd` | MEDIUM | Business intelligence |
| Trace IDs | `ac_call.trace_id` | LOW | Technical correlation data |
| User Flags | `ac_call_flag.created_by`, `note` | MEDIUM | May contain reviewer identity |

### Risk Assessment

- **Data Breach Impact**: SEVERE
  - Full LLM conversations exposed
  - Potential PII exposure
  - Competitive intelligence leak
  - Regulatory fines (GDPR: up to 4% global revenue)

- **Insider Threat**: HIGH
  - Database admins can read all conversations
  - API users can query historical calls
  - Dashboard users see sensitive content

---

## PII and Sensitive Data

### v0.1 Protections (Basic)

Ailuros v0.1 provides **minimal PII protection**:

1. **Text Truncation**: Limits text storage to 50,000 characters (configurable)
   - Location: `AilurosAuditService.MAX_TEXT_LENGTH`
   - Does NOT prevent PII capture, only reduces volume

2. **SHA-256 Hashing**: Stores content hashes for verification
   - Useful for detecting drift, NOT for privacy
   - Hashes are deterministic (same input = same hash)

**⚠️ v0.1 does NOT include:**
- PII detection
- Automatic redaction
- Pattern-based filtering
- Differential privacy

### Recommended Mitigations (Implement Before Production)

#### Option 1: Store-Only-Hash Mode

Completely disable text storage:

**Migration** (`V5__disable_text_storage.sql`):
```sql
-- Make text columns nullable
ALTER TABLE ac_call ALTER COLUMN request_text DROP NOT NULL;
ALTER TABLE ac_call ALTER COLUMN response_text DROP NOT NULL;

-- Add configuration flag
ALTER TABLE ac_call ADD COLUMN text_storage_enabled BOOLEAN DEFAULT FALSE;
```

**Configuration**:
```yaml
ailuros:
  storage:
    store-text: false  # Only store hashes
```

**Service Modification** (`AilurosAuditService.java`):
```java
if (!config.isStoreTextEnabled()) {
    call.setRequestText(null);
    call.setResponseText(null);
    // Hashes still stored for verification
}
```

**Trade-off**:
- ✅ Eliminates PII storage risk
- ❌ Loses debugging capability
- ❌ No diff/comparison features

#### Option 2: Configurable Truncation

Store only first N characters:

```yaml
ailuros:
  storage:
    max-request-length: 1000   # First 1000 chars only
    max-response-length: 2000  # First 2000 chars only
```

**Trade-off**:
- ✅ Reduces exposure surface
- ⚠️ PII may still be in first N chars
- ✅ Preserves some debugging utility

#### Option 3: PII Detection & Redaction (v0.2 Feature)

**Planned Implementation**:
```java
@Service
public class PiiDetectionService {

    // Regex patterns for common PII
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    // ... more patterns

    public String redact(String text) {
        text = EMAIL.matcher(text).replaceAll("[EMAIL_REDACTED]");
        text = SSN.matcher(text).replaceAll("[SSN_REDACTED]");
        text = CREDIT_CARD.matcher(text).replaceAll("[CC_REDACTED]");
        return text;
    }
}
```

**Configuration**:
```yaml
ailuros:
  pii-detection:
    enabled: true
    patterns:
      - email
      - ssn
      - credit-card
      - phone
    custom-patterns:
      - "internal-id-\\d{6}"  # Custom regex
```

**Trade-off**:
- ✅ Balances privacy and utility
- ⚠️ Regex may miss context-specific PII
- ⚠️ False positives (over-redaction)

#### Option 4: Field-Level Encryption (Enterprise)

Encrypt `request_text` and `response_text` at the application layer:

```java
@Service
public class FieldEncryptionService {

    private final SecretKey key = loadEncryptionKey();

    public String encrypt(String plaintext) {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    public String decrypt(String ciphertext) {
        // Reverse process
    }
}
```

**Store encrypted in database**:
```java
call.setRequestText(encryptionService.encrypt(requestText));
call.setResponseText(encryptionService.encrypt(responseText));
```

**Trade-off**:
- ✅ Data encrypted at rest
- ✅ Requires key access for reading
- ❌ Cannot query/search encrypted fields
- ❌ Key management complexity

---

## Access Control

### Database-Level Security

#### 1. Least Privilege

Create separate database roles:

```sql
-- Read-only role for dashboard
CREATE ROLE ailuros_reader;
GRANT SELECT ON ac_call, ac_call_flag, ac_prompt_template TO ailuros_reader;

-- Write role for audit service
CREATE ROLE ailuros_writer;
GRANT SELECT, INSERT ON ac_call TO ailuros_writer;
GRANT SELECT, INSERT ON ac_call_flag TO ailuros_writer;

-- Admin role (for migrations only)
CREATE ROLE ailuros_admin;
GRANT ALL ON ALL TABLES IN SCHEMA public TO ailuros_admin;
```

**Application configuration**:
```yaml
spring:
  datasource:
    read-only:
      url: jdbc:postgresql://localhost:5432/mcpgateway
      username: ailuros_reader
      password: ${READER_PASSWORD}
    read-write:
      url: jdbc:postgresql://localhost:5432/mcpgateway
      username: ailuros_writer
      password: ${WRITER_PASSWORD}
```

#### 2. Row-Level Security (RLS)

Restrict access by project:

```sql
-- Enable RLS
ALTER TABLE ac_call ENABLE ROW LEVEL SECURITY;

-- Policy: Users can only see calls from their projects
CREATE POLICY project_isolation ON ac_call
    FOR SELECT
    USING (project_key = current_setting('app.current_project')::TEXT);

-- Set project context in application
SET app.current_project = 'project_alpha';
```

### API-Level Security

#### 1. Authentication

**Required**: Use Spring Security with JWT or OAuth2:

```java
@Configuration
@EnableWebSecurity
public class AilurosSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ailuros/health").permitAll()
                .requestMatchers("/api/ailuros/**").authenticated()
            )
            .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt);
        return http.build();
    }
}
```

#### 2. Authorization

**Implement role-based access**:

```java
@PreAuthorize("hasRole('AILUROS_VIEWER')")
@GetMapping("/api/ailuros/calls")
public Page<CallListDTO> getCalls(...) { ... }

@PreAuthorize("hasRole('AILUROS_ADMIN')")
@PostMapping("/api/ailuros/calls/{id}/flag")
public FlagDTO createFlag(...) { ... }
```

#### 3. Rate Limiting

Prevent abuse:

```java
@RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS)
@GetMapping("/api/ailuros/calls")
public Page<CallListDTO> getCalls(...) { ... }
```

### Dashboard-Level Security

#### 1. Single Sign-On (SSO)

Integrate with corporate identity provider:

```javascript
// Next.js Auth.js configuration
import NextAuth from "next-auth"
import GoogleProvider from "next-auth/providers/google"

export default NextAuth({
  providers: [
    GoogleProvider({
      clientId: process.env.GOOGLE_CLIENT_ID,
      clientSecret: process.env.GOOGLE_CLIENT_SECRET,
    }),
  ],
  callbacks: {
    async session({ session, token }) {
      session.user.role = token.role; // From JWT
      return session;
    },
  },
})
```

#### 2. Content Security Policy (CSP)

Prevent XSS:

```javascript
// next.config.js
module.exports = {
  headers: async () => [
    {
      source: '/:path*',
      headers: [
        {
          key: 'Content-Security-Policy',
          value: "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline';"
        },
      ],
    },
  ],
}
```

---

## Data Retention

### Default Retention (Configurable)

v0.1 does NOT implement automatic deletion. **You must configure this.**

### Recommended Policy

```yaml
ailuros:
  retention:
    default-days: 30           # Keep most calls for 30 days
    flagged-calls-days: 90     # Keep flagged calls longer
    error-calls-days: 60       # Keep errors for investigation
    archive-to-s3: true        # Archive before deletion
```

### Implementation (v0.2)

**Scheduled cleanup job**:
```java
@Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
public void cleanupOldCalls() {
    Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);

    // Archive to S3 first
    List<AcCall> oldCalls = callRepository.findByCreatedAtBefore(cutoff);
    s3Service.archive(oldCalls);

    // Then delete
    callRepository.deleteByCreatedAtBefore(cutoff);

    log.info("Deleted {} old calls", oldCalls.size());
}
```

### Compliance Considerations

- **GDPR Right to Erasure**: Implement user data deletion
- **CCPA**: Allow users to request data deletion
- **HIPAA**: Retain for 6 years (healthcare)
- **SOX**: Retain for 7 years (financial)

---

## Encryption

### 1. Encryption at Rest

**Database-level encryption**:

```bash
# PostgreSQL with transparent data encryption (TDE)
# Requires enterprise PostgreSQL or cloud provider feature

# Example: AWS RDS with encryption enabled
aws rds create-db-instance \
  --db-instance-identifier mcpgateway \
  --storage-encrypted \
  --kms-key-id arn:aws:kms:us-east-1:123456789:key/...
```

**Application-level encryption** (see Field-Level Encryption above)

### 2. Encryption in Transit

**Enforce HTTPS**:

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

**Database connection encryption**:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mcpgateway?sslmode=require
```

### 3. Key Management

**Use a secrets manager**:

```java
// AWS Secrets Manager
@Configuration
public class SecretsConfig {

    @Bean
    public AWSSecretsManager secretsManager() {
        return AWSSecretsManagerClientBuilder.defaultClient();
    }

    @Bean
    public String encryptionKey(AWSSecretsManager manager) {
        GetSecretValueRequest request = new GetSecretValueRequest()
            .withSecretId("ailuros/encryption-key");
        GetSecretValueResult result = manager.getSecretValue(request);
        return result.getSecretString();
    }
}
```

**Never hardcode keys in source code or config files.**

---

## Compliance

### GDPR (EU)

**Requirements**:
1. **Data Minimization**: Only store necessary data
2. **Purpose Limitation**: Use data only for stated purposes
3. **Storage Limitation**: Delete after retention period
4. **Data Subject Rights**: Implement erasure, portability, access

**Implementation**:
```java
@Service
public class GdprComplianceService {

    // Right to access
    public List<AcCall> getUserData(String userId) {
        return callRepository.findByCreatedBy(userId);
    }

    // Right to erasure
    public void deleteUserData(String userId) {
        List<AcCall> calls = callRepository.findByCreatedBy(userId);
        calls.forEach(call -> {
            call.setRequestText("[DELETED]");
            call.setResponseText("[DELETED]");
        });
        callRepository.saveAll(calls);
    }

    // Right to portability
    public byte[] exportUserData(String userId) {
        List<AcCall> calls = getUserData(userId);
        return JsonExporter.toJson(calls);
    }
}
```

### CCPA (California)

Similar to GDPR, plus:
- **Right to Know**: Disclose data collected
- **Right to Delete**: Delete personal information
- **Right to Opt-Out**: Stop selling data (N/A for Ailuros)

### HIPAA (Healthcare)

If processing **Protected Health Information (PHI)**:

1. **Business Associate Agreement (BAA)**: Required with LLM provider
2. **Audit Logging**: Already provided by Ailuros
3. **Access Controls**: Implement role-based access
4. **Encryption**: Required at rest and in transit
5. **Minimum Necessary**: Redact PHI where possible

**⚠️ Consult legal counsel before processing PHI.**

---

## Incident Response

### Breach Detection

**Monitor for suspicious activity**:

```java
@Service
public class SecurityMonitoringService {

    @Scheduled(fixedDelay = 60000)
    public void detectAnomalies() {
        // Detect mass data exports
        List<User> suspiciousUsers = findUsersWithHighQueryVolume();

        // Detect unusual query patterns
        List<String> suspiciousQueries = findQueriesWithoutFilters();

        // Alert security team
        if (!suspiciousUsers.isEmpty()) {
            alertService.sendSecurityAlert("High-volume data access detected", suspiciousUsers);
        }
    }
}
```

### Breach Response Plan

1. **Detect**: Automated monitoring triggers alert
2. **Contain**: Disable affected accounts, rotate keys
3. **Assess**: Determine scope of breach (which calls exposed?)
4. **Notify**: Inform affected users, regulators (72 hours for GDPR)
5. **Remediate**: Patch vulnerability, implement additional controls
6. **Review**: Post-mortem, update security policies

### Example Breach Scenario

**Scenario**: Unauthorized API access exports 10,000 call records

**Response**:
```bash
# 1. Revoke API keys
UPDATE api_keys SET revoked = true WHERE user_id = 'attacker123';

# 2. Identify exposed calls
SELECT id, trace_id, created_at
FROM ac_call
WHERE id IN (SELECT call_id FROM audit_log WHERE user_id = 'attacker123');

# 3. Notify affected users
# (Extract unique users from exposed calls, send breach notification)

# 4. Rotate all API keys
# (Force password reset for all dashboard users)
```

---

## Security Checklist

Use this checklist before **production deployment**:

### Configuration
- [ ] Enable HTTPS for API
- [ ] Enable SSL for database connections
- [ ] Configure text truncation or disable text storage
- [ ] Set up data retention policies
- [ ] Configure secrets manager (no hardcoded keys)

### Access Control
- [ ] Implement authentication (JWT, OAuth2)
- [ ] Implement authorization (RBAC)
- [ ] Create least-privilege database roles
- [ ] Enable row-level security (if multi-tenant)
- [ ] Set up rate limiting

### Data Protection
- [ ] Enable database encryption at rest
- [ ] Implement field-level encryption (if required)
- [ ] Configure PII detection (if v0.2+)
- [ ] Test data anonymization
- [ ] Verify text truncation works

### Monitoring
- [ ] Set up security logging
- [ ] Configure anomaly detection
- [ ] Enable audit trail for admin actions
- [ ] Set up breach detection alerts
- [ ] Test incident response plan

### Compliance
- [ ] Conduct privacy impact assessment (PIA)
- [ ] Create data processing agreement (DPA)
- [ ] Implement GDPR data subject rights
- [ ] Document data retention policy
- [ ] Obtain legal approval for PII processing

### Testing
- [ ] Penetration test API endpoints
- [ ] Verify access controls (try to access other project's data)
- [ ] Test data export/deletion flows
- [ ] Verify encryption at rest and in transit
- [ ] Simulate breach scenario

---

## Questions?

**Security concerns or questions?**
- Email: security@mcpgateway.com
- Security Policy: https://mcpgateway.com/security
- Vulnerability Disclosure: https://mcpgateway.com/security/disclosure

**Remember**: Security is an ongoing process, not a one-time checklist. Regularly review and update your security posture as threats evolve.
