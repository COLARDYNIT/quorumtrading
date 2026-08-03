#!/usr/bin/env bash
# Fail a pull request when the lines it CHANGED are not tested well enough.
#
# This is the gate that check-backend-coverage.sh cannot be. That one enforces an aggregate
# floor, which a single PR barely moves: a 500-line untested class shifts a 60%-of-190k-lines
# figure by a fraction of a point and sails straight through. Coverage decays exactly that way —
# never in one visible drop, always in increments too small for an aggregate gate to notice.
#
# So the floors here are deliberately set ABOVE each stack's current aggregate:
#
#     backend   floor 80%   aggregate 61.9% instruction  (measured 2026-07-29)
#     frontend  floor 30%   aggregate  5.3% statements   (measured 2026-07-29)
#
# which makes the arithmetic do the work — every PR that passes raises total coverage, and the
# aggregate can only climb toward the 80% target. Raise these as the aggregates rise. Lowering
# one to make a build pass is the one change that defeats the whole file.
#
# Measured patch coverage when these numbers were chosen: backend 86% over a 990-line sample,
# frontend 13%–41% over two samples. The backend floor is therefore already met by current
# practice; the frontend floor is a genuine lift, and is set low on purpose so it is a ratchet
# rather than a wall.
#
# Nothing here needs a token or a hosted service, so it runs on pull requests from forks — which
# a hosted scanner cannot do, because GitHub withholds secrets from fork builds.
set -euo pipefail

STACK="${1:-}"
BASE_REF="${BASE_REF:-origin/main}"
MIN_BACKEND="${MIN_BACKEND_DIFF:-80}"
MIN_FRONTEND="${MIN_FRONTEND_DIFF:-30}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if ! command -v diff-cover >/dev/null 2>&1; then
    echo "::error::diff-cover is not installed. Run: pip install diff-cover"
    exit 1
fi

# diff-cover needs the merge base to exist locally. A shallow clone has neither, and the failure
# mode is a silent empty diff — "no lines with coverage information", exit 0, gate reports green
# having checked nothing. Verify rather than trust.
if ! git rev-parse --verify --quiet "$BASE_REF" >/dev/null; then
    echo "::error::Base ref '$BASE_REF' is not available. The checkout needs fetch-depth: 0."
    exit 1
fi

check_backend() {
    local report="quorumtrading-backend/quorumtrading-coverage/target/site/jacoco-aggregate/jacoco.xml"
    if [[ ! -f "$report" ]]; then
        echo "::error::No aggregate coverage XML at $report — did 'mvnw verify' run?"
        exit 1
    fi

    # One --src-root per module: JaCoCo records a package and a filename, and diff-cover
    # reconstructs the repo path by joining a root onto them. A module missing from this list is
    # simply invisible to the gate, so the list is globbed rather than hand-maintained.
    local roots=()
    while IFS= read -r dir; do roots+=("$dir"); done \
        < <(find quorumtrading-backend -maxdepth 4 -type d -path '*/src/main/java' | sort)
    if [[ ${#roots[@]} -eq 0 ]]; then
        echo "::error::Found no backend source roots to map coverage onto."
        exit 1
    fi
    echo "Backend: ${#roots[@]} source roots, base $BASE_REF, floor ${MIN_BACKEND}%"

    diff-cover "$report" \
        --compare-branch="$BASE_REF" \
        --src-roots "${roots[@]}" \
        --fail-under="$MIN_BACKEND"
}

check_frontend() {
    local report="quorumtrading-frontend/coverage/lcov.info"
    if [[ ! -f "$report" ]]; then
        echo "::error::No lcov report at $report — did 'npm test -- --coverage' run?"
        exit 1
    fi

    # Jest writes SF: paths relative to the frontend package; git reports them relative to the
    # repo root, and nothing matches until they agree. --src-roots does not help here: it is
    # applied to Cobertura and JaCoCo reports, not to lcov.
    local rooted
    rooted="$(mktemp -t lcov-rooted.XXXXXX)"
    trap 'rm -f "$rooted"' RETURN
    sed 's|^SF:|SF:quorumtrading-frontend/|' "$report" > "$rooted"

    echo "Frontend: base $BASE_REF, floor ${MIN_FRONTEND}%"
    diff-cover "$rooted" \
        --compare-branch="$BASE_REF" \
        --fail-under="$MIN_FRONTEND"
}

case "$STACK" in
    backend)  check_backend ;;
    frontend) check_frontend ;;
    *)
        echo "usage: $0 {backend|frontend}" >&2
        echo "  BASE_REF, MIN_BACKEND_DIFF, MIN_FRONTEND_DIFF override the defaults." >&2
        exit 2
        ;;
esac
