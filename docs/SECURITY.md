# 🔒 Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| 1.2.x (latest) | ✅ Actively maintained |
| 1.1.x | ⚠️ Critical fixes only |
| < 1.1.0 | ❌ No longer supported |

---

## Reporting a Vulnerability

**Please do NOT open a public GitHub Issue for security vulnerabilities.**

Use GitHub's private [Security Advisory](https://github.com/REBEL-ROOT/omni-browser/security/advisories/new) system to report responsibly.

### What to include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact (data exposure, privilege escalation, etc.)
- App version and Android version affected
- Any proof-of-concept code (if applicable)

### What to expect:
- **Acknowledgement** within 72 hours
- **Assessment** within 7 days
- **Fix or mitigation** within 30 days for critical issues
- **Public disclosure** coordinated after fix is released

We deeply appreciate responsible disclosure. Contributors who report valid, previously unknown vulnerabilities may be credited in the release notes.

---

## Security Architecture Notes

- All sensitive files in the **Biometric Vault** are encrypted with **AES-256-GCM** using Android Keystore hardware-backed keys.
- Private browsing sessions use **GeckoView's native private context** — no cookies, history, or cache are persisted.
- WebExtensions run in the **GeckoView sandbox** and cannot access native Android APIs directly.
- Network requests from extensions are governed by GeckoView's built-in security model.
