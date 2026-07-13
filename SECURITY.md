# Security Guidelines for TraningApp

## Overview

TraningApp uses **Jasypt (Java Simplified Encryption)** to protect sensitive data such as database credentials and email authentication information. This document provides guidelines for managing encryption keys and sensitive configuration.

---

## Encryption Configuration

### Jasypt Setup

The application uses the **PBEWithHmacSHA512AndAES_256** algorithm for password-based encryption:

- **Algorithm**: PBEWithHmacSHA512AndAES_256 (Strong and modern)
- **Key Derivation**: PBKDF2 with 1000 iterations
- **Provider**: SunJCE

Configuration is defined in `src/main/resources/bootstrap.yml`:

```yaml
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD:local-dev-password}
    algorithm: PBEWithHmacSHA512AndAES_256
    key-obtention-iterations: 1000
    provider-name: SunJCE
```

---

## Encrypted Configuration Values

### Encrypted Fields

| Field                  | File                     | Encrypted? | Scope            |
| ---------------------- | ------------------------ | ---------- | ---------------- |
| `db.password` (GCP)    | `pom.xml`                | ✅ Yes     | Production       |
| `spring.mail.password` | `application.properties` | ✅ Yes     | All Environments |
| `db.password` (local)  | `pom.xml`                | ❌ No      | Development Only |

### Encryption Format

All encrypted values use the Jasypt format:

```properties
property.name=ENC(encrypted-base64-string)
```

Example:

```properties
spring.datasource.password=ENC(D+5u5m2vX0P68vE4R3K5uA==)
spring.mail.password=ENC(a8aU+XwSxX0K8e3dR5Z7uQ==)
```

---

## Environment Variables

All sensitive information must be managed via environment variables:

| Environment Variable        | Purpose                               | Required       | Scope           |
| --------------------------- | ------------------------------------- | -------------- | --------------- |
| `JASYPT_ENCRYPTOR_PASSWORD` | Master password for Jasypt decryption | ✅ Yes         | All             |
| `SPRING_MAIL_USERNAME`      | Gmail account (override)              | ❌ No          | Optional        |
| `SPRING_MAIL_PASSWORD_ENC`  | Encrypted Gmail app password          | ⚠️ Conditional | If using Jasypt |
| `GOOGLE_CLIENT_ID`          | Google OAuth2 Client ID               | ✅ Yes         | All             |
| `GOOGLE_CLIENT_SECRET`      | Google OAuth2 Client Secret           | ✅ Yes         | All             |

---

## How to Encrypt Configuration Values

### Prerequisites

1. Java and Maven installed
2. Access to `JASYPT_ENCRYPTOR_PASSWORD` value

### Steps to Encrypt a Value

**1. Set the master password:**

```bash
export JASYPT_ENCRYPTOR_PASSWORD=your-master-password
```

**2. Use EncryptTool to encrypt the plaintext:**

```bash
mvn clean compile exec:java \
  -Dexec.mainClass="com.example.training.util.EncryptTool" \
  -Dexec.args="plaintext-to-encrypt"
```

**3. Copy the encrypted output:**
The tool outputs: `Encrypted: ENC(base64-string)`

Copy the `ENC(base64-string)` value to your configuration.

### Example: Encrypt Gmail App Password

```bash
export JASYPT_ENCRYPTOR_PASSWORD=production-master-password
mvn clean compile exec:java \
  -Dexec.mainClass="com.example.training.util.EncryptTool" \
  -Dexec.args="your-gmail-app-password"
```

Output:

```
Encrypted: ENC(a8aU+XwSxX0K8e3dR5Z7uQ==)
```

Then add to `application.properties`:

```properties
spring.mail.password=ENC(a8aU+XwSxX0K8e3dR5Z7uQ==)
```

---

## Development Environment Setup

### Local Development

1. Create a `.env` file in the project root (see `.env.example`):

   ```
   JASYPT_ENCRYPTOR_PASSWORD=local-dev-password
   GOOGLE_CLIENT_ID=your-local-google-client-id
   GOOGLE_CLIENT_SECRET=your-local-google-secret
   ```

2. Source the environment before running:
   ```bash
   source .env
   mvn spring-boot:run
   ```

**Note**: The `.env` file should **NEVER** be committed to version control. It is in `.gitignore`.

---

## Production Environment

### Key Management Strategy

**❌ DO NOT hardcode secrets in configuration files**

**✅ DO set environment variables at deployment time:**

- Container environments (Docker/Kubernetes): Use secret management tools (e.g., Kubernetes Secrets)
- Virtual machines: Use systemd environment files or deployment scripts
- Cloud platforms: Use managed secret services (e.g., GCP Secret Manager)

### Deployment Checklist

- [ ] `JASYPT_ENCRYPTOR_PASSWORD` is set via secure channel (NOT in code)
- [ ] `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are configured
- [ ] All encrypted values in `application.properties` are correct
- [ ] `.env` and `.env.local` files are NOT deployed
- [ ] Application starts without errors and verifies decryption

---

## Security Best Practices

### 1. Master Password Management

- ✅ Store `JASYPT_ENCRYPTOR_PASSWORD` in a secure secrets manager
- ❌ Do NOT hardcode in source code
- ❌ Do NOT store in `.env` files in production

### 2. Version Control

- ✅ Commit encrypted values (ENC(...)) to git
- ✅ Commit `.env.example` with dummy values
- ❌ Never commit `.env` files
- ❌ Never commit actual secrets

### 3. Configuration Files

- ✅ Use environment variables for sensitive data
- ✅ Use Jasypt for database passwords
- ❌ Do NOT store plaintext passwords
- ❌ Do NOT store API keys

### 4. CI/CD Pipelines

- ✅ Inject secrets at runtime via GitHub Secrets, GitLab CI Variables, etc.
- ✅ Use short-lived credentials
- ❌ Do NOT log sensitive values
- ❌ Do NOT commit secrets to CI configuration

### 5. Database Credentials

- ✅ Use Jasypt encryption (as currently implemented)
- ✅ Rotate credentials regularly
- ✅ Use principle of least privilege (e.g., `springuser` with limited permissions)
- ❌ Do NOT use root credentials

---

## Future Improvements

### Phase 2: Advanced Secret Management

- Integrate Spring Cloud Config Server
- Use HashiCorp Vault for centralized secret management
- Implement automatic secret rotation

### Phase 3: Monitoring & Auditing

- Log all configuration access attempts
- Alert on failed decryption attempts
- Monitor for unauthorized secret access

---

## Troubleshooting

### Application fails to start with "Decryption failed" error

**Symptom**: `org.jasypt.exceptions.EncryptionOperationNotPossibleException`

**Solutions**:

1. Verify `JASYPT_ENCRYPTOR_PASSWORD` environment variable is set correctly
2. Ensure the master password matches the one used to encrypt the values
3. Check that encrypted values use the correct format: `ENC(...)`

### Gmail authentication fails

**Symptom**: `SMTPAuthenticationException` or `535 5.7.8 Username and password not accepted`

**Solutions**:

1. Verify `SPRING_MAIL_PASSWORD_ENC` encrypted value is correct
2. Use an **app password** (not your Google account password) for Gmail
3. Ensure 2-factor authentication is enabled on the Gmail account

### Can't run EncryptTool

**Symptom**: `Error: JASYPT_ENCRYPTOR_PASSWORD environment variable not set`

**Solution**: Ensure you set the environment variable before running:

```bash
export JASYPT_ENCRYPTOR_PASSWORD=your-password
```

---

## References

- [Jasypt Spring Boot Documentation](https://github.com/ulisesbocchio/jasypt-spring-boot)
- [Spring Boot Externalized Configuration](https://spring.io/projects/spring-cloud-config)
- [OWASP: Sensitive Data Exposure](https://owasp.org/www-community/Sensitive_Data_Exposure)
