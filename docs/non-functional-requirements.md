# Non-Functional Requirements: Architeezy Archi Connector

## Overview

This document defines the quality standards, performance targets, and technical constraints for the
Architeezy Archi Connector plugin.

## NFR-1: Performance

- NFR-1.1: **Sync Speed.** Push/Pull must complete within 5s for small models (<1,000 elements),
  within 30s for medium models (1,000–10,000 elements), assuming standard broadband latency. For
  large models (>10,000 elements) no hard SLA applies; a progress indicator is mandatory.
- NFR-1.2: **Merge Efficiency.** Automatic 3-way merge calculations for average conflict sets (<50
  objects) must complete within 2s.
- NFR-1.3: **UI Responsiveness.** All network and compute-intensive operations (Sync, Merge, Fetch)
  must run in background threads to prevent Archi UI freezing.
- NFR-1.4: **Memory Footprint.** The plugin should not increase Archi’s baseline memory consumption
  by more than 50MB during idle states.
- NFR-1.5: **Delta Optimization.** The system must minimize data transfer by transmitting only
  modified model fragments rather than the entire file.

## NFR-2: Reliability

- NFR-2.1: **Data Integrity.** The plugin must never modify the local `.archimate` file until a
  merge is successfully validated. A snapshot of the pre-merge state must be retained throughout the
  merge session and automatically discarded once the user commits or cancels.
- NFR-2.2: **Secure Storage.** Authentication tokens and sensitive configuration must be stored
  using the Eclipse Secure Preferences (OS-level encryption), never in plain text.
- NFR-2.3: **Fault Tolerance.** Graceful recovery from network interruptions. If a connection is
  lost during Sync, the local model must remain in a consistent, uncorrupted state.
- NFR-2.4: **Encryption.** All communication with the Architeezy API must be conducted over HTTPS
  using TLS 1.2 or higher.

## NFR-3: Usability

- NFR-3.1: **Native Look and Feel.** The user interface must utilize Archi’s native SWT/JFace
  components to ensure a seamless visual experience.
- NFR-3.2: **Progress Feedback.** Any operation exceeding 500ms must display a non-blocking progress
  indicator or status bar update.
- NFR-3.3: **Error Messaging.** Error messages must be user-friendly, providing clear descriptions
  of the issue and actionable "Retry" or "Cancel" options.
- NFR-3.4: **Accessibility.** UI components must support keyboard navigation and be compatible with
  standard screen readers as per Eclipse RCP standards.

## NFR-4: Quality

- NFR-4.1: **Compatibility.** The plugin must support the two most recent major versions of Archi
  (currently 5.x and 4.x).
- NFR-4.2: **Code Standards.** The codebase must adhere to standard Java coding conventions and
  undergo static analysis (e.g., Checkstyle or SonarQube).
- NFR-4.3: **Test Coverage.** Business logic for merging and delta-calculation must maintain ≥80%
  unit test coverage.
- NFR-4.4: **Modularity.** The connector logic must be decoupled from the UI to allow for automated
  headless testing.

## NFR-5: Portability

- NFR-5.1: **Cross-Platform Support.** Full functionality on Windows, macOS (Intel and Apple
  Silicon), and Linux, consistent with Archi’s supported platforms.
- NFR-5.2: **Java Compliance.** Must be compatible with the Java Runtime Environment (JRE) bundled
  with the target Archi version (minimum Java 17).
- NFR-5.3: **Update Mechanism.** Support for plugin updates via standard Eclipse Update Sites (P2
  repositories).

## NFR-6: Compliance

- NFR-6.1: **Licensing.** All third-party libraries must use permissive licenses (MIT, Apache 2.0,
  or EPL) compatible with Archi’s open-source nature.
- NFR-6.2: **Data Privacy.** No telemetry or user modeling data may be transmitted to third parties.
  Compliance with GDPR for any user-identifiable session data.
