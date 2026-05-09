# Upgrade Plan: TraningApp (20260509045031)

- **Generated**: 2026-05-09 04:56
- **HEAD Branch**: master
- **HEAD Commit ID**: 3ad0d648bdec4e32db3e6bbfd85e849f8ed4e920

## Available Tools

**JDKs**
- JDK 17: /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin
- JDK 21.0.7: /Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin (required by step 1 and final validation)
- JDK 23.0.2: /Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home/bin

**Build Tools**
- Maven Wrapper: 3.9.12 (compatible with Java 21)
- Maven CLI: not installed on host; wrapper will be used

## Guidelines

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred.

## Options

- Working branch: appmod/java-upgrade-20260509045031
- Run tests before and after the upgrade: true

## Upgrade Goals

- Upgrade the Java runtime target to the latest LTS version, Java 21.

## Technology Stack

| Technology/Dependency               | Current                                        | Min Compatible     | Why Incompatible / Notes                                  |
| ---------------------------------- | ---------------------------------------------- | ------------------ | --------------------------------------------------------- |
| Java                               | 21                                             | 21                 | User requested latest LTS; project already targets 21      |
| Spring Boot                        | 3.4.2                                          | 3.4.2              | Compatible with Java 21                                    |
| Maven Wrapper                      | 3.9.12                                         | 3.9.0              | Supports Java 21                                           |
| maven-compiler-plugin              | managed by Spring Boot parent                  | 3.11.0             | Required for Java 21 bytecode compilation                  |
| org.seasar.doma.boot:doma-spring-boot-starter | 1.7.0                             | 1.7.0+             | Compatible with Spring Boot 3.x                            |
| org.springframework.boot:spring-boot-starter-web | 3.4.2                            | 3.4.2+             | Compatible with Java 21                                    |
| org.projectlombok:lombok            | 1.18.30                                       | 1.18.30            | Compatible with Java 21                                    |
| com.mysql:mysql-connector-j         | managed by Spring Boot parent                 | 8.0.x              | Compatible with Java 21                                    |

## Derived Upgrades

- No Spring Boot version upgrade is required because the project is already on Spring Boot 3.4.2, which is compatible with Java 21.
- No Maven wrapper upgrade is required because the wrapper is already at Maven 3.9.12, which supports Java 21.
- No direct Java version dependency changes are required in `pom.xml` because `java.version` is already set to 21.

## Upgrade Steps

- Step 1: Confirm Environment and Toolchain
  - **Rationale**: Ensure the required Java 21 runtime and Maven wrapper are available before validation.
  - **Changes to Make**:
    - Confirm JDK 21 availability and use the Maven wrapper for builds.
    - Do not modify project source unless compatibility issues are found.
  - **Verification**: `./mvnw -q -v` with JDK 21, expected output includes Java 21 and Maven 3.9.12.

- Step 2: Establish Baseline with Java 21
  - **Rationale**: Verify the current project configuration compiles and tests under the target runtime before concluding the upgrade.
  - **Changes to Make**:
    - Run the existing build and test suite using Java 21.
  - **Verification**: `./mvnw clean test-compile -q && ./mvnw clean test -q` with JDK 21.

- Step 3: Validate Current Java 21 Runtime Configuration
  - **Rationale**: The project already targets Java 21, so the main activity is to confirm no hidden source or dependency issues remain.
  - **Changes to Make**:
    - Verify there are no JDK 21 compatibility issues in source and build plugins.
    - Update any minimal build plugin versions if necessary after test results.
  - **Verification**: `./mvnw clean test-compile -q` with JDK 21.

- Step 4: Final Validation and Cleanup
  - **Rationale**: Ensure the upgrade goal is fully met with a clean build and passing test suite.
  - **Changes to Make**:
    - Resolve any failures discovered in the baseline or validation steps.
    - Confirm the project remains stable on Java 21.
  - **Verification**: `./mvnw clean test -q` with JDK 21.

## Key Challenges

- **Existing Java 21 target detection**
   - **Challenge**: The project already declares `java.version=21`; the upgrade path is mostly validation rather than code migration.
   - **Strategy**: Use the Maven wrapper and JDK 21 to verify that the current configuration is already correct.

- **Build tool availability**
   - **Challenge**: Maven CLI is not installed on the host, so the wrapper must be used for reproducible validation.
   - **Strategy**: Rely on `.mvn/wrapper/maven-wrapper.properties` and verify the wrapper works with Java 21.

- **JDK source compatibility scan**
   - **Challenge**: Ensure no hidden use of removed or encapsulated JDK internals exists.
   - **Strategy**: Grep source for risky patterns; only a commented `javax.sql.DataSource` import was found, indicating low risk.
