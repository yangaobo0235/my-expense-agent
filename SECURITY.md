# Security Policy

## Supported Version

Security fixes are applied to the latest revision of the `main` branch.

## Reporting a Vulnerability

Please do not disclose credentials, tokens, personal data, or an exploitable vulnerability in a public issue.

Use the repository's private security advisory page to report a vulnerability. Include the affected component, reproduction steps, impact, and any suggested mitigation. Remove secrets and personal information from logs or screenshots before attaching them.

## Repository Secrets

- Keep local values in `.env.local`; it is excluded from Git.
- Commit placeholders only in `*.env.example` files.
- Rotate a credential immediately if it is committed or posted publicly.
- Use a dedicated database account, MinIO account, and MCP service token in production.
- Enable secure session cookies when deploying behind HTTPS.
