# Contributed injection results

One `results-<agent>.json` per agent, from `inject-eval.py` (API path) or `score.py` (manual
path). See [`../CONTRIBUTING.md`](../CONTRIBUTING.md).

Each file carries per-cell compliance/flag counts and truncated transcripts, so scoring is
auditable, not just asserted. Results are version-stamped in `meta` because they rot as models
change — a 2026 run is not a claim about the same model a year later.

The maintainers' own runs (the local gradient, the Claude tiers) live one level up as
`../results-*.json` and are rolled up in [`../results-summary.md`](../results-summary.md);
contributed agents extend that matrix here.
