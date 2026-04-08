# Release Publishing

This project uses gated publishing for release tags.

Workflow: `.github/workflows/release-gated-publish.yml`

## What it automates

- Step 3 (`attach qualification evidence in release notes`):
  - Release body includes workflow run URL and `release-qualification-pack` artifact reference.
  - Pack includes `release-notes-qualification.md` for copy-safe evidence text.

- Step 4 (`do not publish if any gate fails`):
  - `publish` job depends on `qualify` and runs only if all qualification gates pass.
  - Any failed SLO gate stops release publication.

## Release evidence payload

Artifact: `release-qualification-pack`

- `release-qualification-report.md`
- `perf-regression-report.md`
- `soak-qualification-report.md`
- `transport-qualification-report.md`
- `recovery-qualification-metrics.txt`
- `release-notes-qualification.md`
