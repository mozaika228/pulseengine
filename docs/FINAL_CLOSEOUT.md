# Final Closeout

This checklist defines what remains for complete closeout evidence.

## 1. Proof runs

Required workflow runs:

- `Finalization Smoke` on `main`
- `Staging Canary`
- `Release Qualification`
- `Release Gated Publish` (test tag or release tag)

All must finish green with artifacts uploaded.

## 2. Security policy precedence

Active policy is `.github/SECURITY.md`.

Repository root `SECURITY.md` should be treated as legacy text until it is aligned or removed in a local-shell-enabled maintenance pass.

## 3. Evidence to attach in release notes

- `release-qualification-pack`
- `finalization-proof-pack`
- `staging-canary-report`

## 4. Completion definition

Closeout is complete when:

- release gating is enforced by workflow result
- canary report is `PASS`
- release qualification report is `PASS`
- security reporting path is private advisory only
