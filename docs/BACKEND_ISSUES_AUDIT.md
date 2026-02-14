# Backend Issues Audit (Updated)

Date: 2026-02-14

This file tracks the backend stability/security issues discovered and their current remediation status.

## ✅ Remediated in codebase

1. **Secrets removed from default runtime config**
   - `application.properties` no longer contains hardcoded secrets for JWT, SMTP, or Backblaze B2.
   - Security-sensitive values are now environment-driven.

2. **Gradle wrapper bootstrap repaired**
   - `gradle/wrapper/gradle-wrapper.jar` restored.
   - `gradlew` marked executable.

3. **Documentation/build alignment improved**
   - README minimum Java version now matches Gradle toolchain expectation.
   - README default server port now matches `application.properties`.

4. **Dependency hygiene improved**
   - OpenSearch client dependencies aligned to the same version.

5. **Safer default runtime behavior**
   - `debug` disabled in default configuration.

6. **Profile consistency improvement**
   - Test profile JWT key names aligned with the main application property contract.

## ⚠️ Follow-up items still recommended

1. **Secret rotation outside repo is still required**
   - Any previously exposed credentials should still be rotated at the provider level.

2. **CI/Environment networking for wrapper distribution**
   - Wrapper now exists, but environments with restricted egress must allow access to Gradle distribution URLs (or use an internal mirror).

3. **JDK policy standardization**
   - Define and enforce a supported JDK matrix in CI to avoid runtime/build-tool incompatibilities.
