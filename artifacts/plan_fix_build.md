# Plan to Fix Build & Import Errors

## Issue Diagnosis
The IDE (using Eclipse Buildship) is failing to configure the project with errors:
- `Can't use Java 21.0.10 and Gradle 8.13 to import Gradle project`
- `Unbound classpath container: 'JRE System Library [JavaSE-21]'`

This is typically caused by:
1. **Gradle vs Buildship compatibility:** The user's IDE plugin might be older and not fully support Gradle 8.13. Downgrading the Gradle wrapper to a stable, older 8.x version (like 8.10 or 8.5) often resolves this.
2. **Missing Local JDK 21:** The IDE might not have a JDK 21 correctly registered in its runtimes.
3. **Toolchain setup:** `java.toolchain.languageVersion` is set to 21, which requires Gradle to download or locate a Java 21 toolchain.

## Proposed Steps
1. Verify if the issue happens on the CLI (`./gradlew build`). If CLI passes but IDE fails, it's strictly an IDE import issue.
2. Downgrade `gradle-wrapper.properties` distributionUrl from `gradle-8.13-bin` to `gradle-8.10-bin` (or 8.5). Gradle 8.10 is known to have stable Java 21 support and better compatibility with older Buildship versions.
3. If necessary, ensure Eclipse/VS Code settings explicitly point to Java 21.
4. Save CLI output traces to `artifacts/logs/build_logs.txt` as per the Antigravity Directives.
