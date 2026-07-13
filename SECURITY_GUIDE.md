# Security Guide for Training App

## SMTP Configuration Security

### ✅ Fixed Issues
- Removed hardcoded Gmail email address from `application.properties`
- Cleaned git history to remove exposed credentials
- Updated template files with placeholder values

### 🔐 Proper Credential Management

#### Environment Variables Setup
1. **Create `.env` file** (never commit this file):
```bash
cp .env.example .env
```

2. **Set your actual credentials in `.env`**:
```bash
SPRING_MAIL_USERNAME=your-actual-gmail@gmail.com
SPRING_MAIL_PASSWORD=your-app-password
```

#### Gmail App Password Setup
1. Enable 2-factor authentication on your Google account
2. Go to https://myaccount.google.com/apppasswords
3. Select "Mail" and "Other (Custom name)"
4. Enter app name: "Training App"
5. Copy the generated 16-character password
6. Use this password as `SPRING_MAIL_PASSWORD`

#### Production Deployment
For production environments, use:
- Encrypted passwords with Jasypt
- Environment-specific configuration files
- Cloud provider secret management services

### 🚨 Security Rules
- NEVER commit actual credentials to version control
- ALWAYS use environment variables for sensitive data
- REGULARLY rotate passwords and tokens
- USE different credentials for development and production

### 🔍 Security Monitoring
- Enable GitGuardian or similar security scanning
- Regularly audit commit history for exposed secrets
- Monitor for unauthorized access attempts

## Additional Security Measures

### Database Security
- Use encrypted database passwords
- Implement connection pooling with secure credentials
- Regular database backups with encryption

### API Security
- Implement rate limiting
- Use HTTPS for all communications
- Validate and sanitize all inputs
- Implement proper authentication and authorization

### Code Security
- Regular dependency updates
- Static code analysis
- Security testing in CI/CD pipeline
