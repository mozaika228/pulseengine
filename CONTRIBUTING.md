# Contributing to PulseEngine

## Requirements
- Java 21
- Maven 3.9+

## Development workflow
1. Fork the repository and create a feature branch from `main`.
2. Keep changes focused and include tests for behavior changes.
3. Run local checks before opening a PR:
   - `mvn -DskipTests=false verify`
   - `mvn -DskipTests test-compile`
4. For performance-sensitive changes, include JMH comparison notes.

## Coding guidelines
- Prioritize deterministic behavior and low-allocation hot paths.
- Avoid introducing locks on the matching critical path unless justified.
- Keep API and message format changes backward-aware and documented.

## Pull requests
- Describe what changed and why.
- Include before/after metrics for latency or throughput if relevant.
- Link related issues.

## Reporting issues
- Include reproduction steps, environment, and expected vs actual behavior.
