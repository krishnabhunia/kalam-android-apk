#!/usr/bin/env bash
# Releases and semantic versions are managed only by the unified Software workflow.
set -euo pipefail
BETA=false
REF=$(git branch --show-current)
while [ "$#" -gt 0 ]; do
  case "$1" in
    --beta) BETA=true; shift ;;
    --ref) REF=${2:?Supply a branch after --ref}; shift 2 ;;
    --help) echo "Usage: ./release.sh [--beta] [--ref branch]. Stable releases publish after merge to main."; exit 0 ;;
    *) echo "Versions are automatic. Use --beta to explicitly publish an optional beta, or merge a reviewed PR for a stable release." >&2; exit 2 ;;
  esac
done
command -v gh >/dev/null || { echo "GitHub CLI is required, or run the Software workflow in GitHub Actions." >&2; exit 1; }
gh workflow run software.yml --ref "$REF" -f "publish_beta=$BETA"
echo "Queued the single Software workflow for $REF (publish beta: $BETA)."
