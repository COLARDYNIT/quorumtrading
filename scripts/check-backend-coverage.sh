#!/usr/bin/env bash
# Fail the build when aggregate backend coverage drops.
#
# Reads the merged report from quorumtrading-coverage, NOT a per-module one: per-module JaCoCo
# counts only the tests that ran inside that module, so it ignores every integration test that
# exercises another module. Gating on those numbers would enforce a figure that is wrong in a
# direction nobody can see.
#
# The thresholds below are a RATCHET, not the goal. The goal is 80%. They sit just under the
# current measured figure so a regression fails immediately, and they are raised as tests land.
# Lowering one to make a build pass defeats the entire point of having it.
set -euo pipefail

MIN_INSTRUCTION="${MIN_INSTRUCTION:-60.0}"
MIN_BRANCH="${MIN_BRANCH:-47.0}"
TARGET=80.0

CSV="quorumtrading-backend/quorumtrading-coverage/target/site/jacoco-aggregate/jacoco.csv"
if [[ ! -f "$CSV" ]]; then
    echo "::error::No aggregate coverage report at $CSV — did 'mvnw verify' run?"
    exit 1
fi

# Command substitution, not `read < <(...)`: awk's printf emits no trailing newline, so `read`
# returns non-zero at EOF and `set -e` killed the script before it printed anything.
COVERAGE=$(awk -F, '
    NR > 1 {
        im += $4; ic += $5;      # INSTRUCTION_MISSED, INSTRUCTION_COVERED
        bm += $6; bc += $7;      # BRANCH_MISSED, BRANCH_COVERED
    }
    END {
        printf "%.1f %.1f", (ic + im ? 100 * ic / (ic + im) : 0), (bc + bm ? 100 * bc / (bc + bm) : 0)
    }' "$CSV")
INSTR="${COVERAGE% *}"
BRANCH="${COVERAGE#* }"

echo "Backend aggregate coverage: instruction ${INSTR}%  branch ${BRANCH}%"
echo "Gate: instruction >= ${MIN_INSTRUCTION}%  branch >= ${MIN_BRANCH}%   (target ${TARGET}%)"

# `if`, not `&&`: under `set -e` a bare awk that exits non-zero takes the whole script down,
# which silently turned this gate into an unconditional failure the first time it ran.
below() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a < b) }'; }

fail=0
if below "$INSTR" "$MIN_INSTRUCTION"; then
    echo "::error::Instruction coverage ${INSTR}% is below the ${MIN_INSTRUCTION}% floor."
    fail=1
fi
if below "$BRANCH" "$MIN_BRANCH"; then
    echo "::error::Branch coverage ${BRANCH}% is below the ${MIN_BRANCH}% floor."
    fail=1
fi
if [[ $fail -eq 1 ]]; then
    echo "Raise coverage, or say why the floor should move — do not lower it to go green."
    exit 1
fi

if below "$INSTR" "$TARGET"; then
    echo "::notice::Above the floor but below the ${TARGET}% target — ${INSTR}%. Raise the floor as you add tests."
fi

echo "Coverage gate passed."
