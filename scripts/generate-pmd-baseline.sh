#!/usr/bin/env bash
# Regenerate quorumtrading-backend/pmd-baseline.properties from the current PMD reports.
#
# The baseline is a FREEZE LIST, the same device as the exempt-class lists in
# HexagonalArchitectureTest: every violation that exists today is recorded so the gate is green
# on arrival and only NEW violations break the build. maven-pmd-plugin reads it via
# <excludeFromFailureFile>, one line per class:
#
#     com.quorumtrading.model.entity.Simulation=CyclomaticComplexity,PreserveStackTrace
#
# Note the granularity: a class is exempted FROM THOSE RULES ENTIRELY, not from its current
# violation count. Adding a 40th cyclomatic-complexity violation to a class already listed for
# CyclomaticComplexity will not fail the build. That is the honest limitation of the plugin's
# mechanism — it is a floor on new *kinds* of problem in old files, plus a hard gate on every
# file that is currently clean, which is where new code lands.
#
# Run this ONLY after fixing violations, to shrink the list. Running it to absorb violations you
# just introduced is the one use that defeats the gate — the diff will show the entries you added
# and reviewers should ask.
#
# The script drives the whole sequence itself rather than reading whatever reports happen to be
# on disk, because doing it by hand is circular and silently produces a nearly-empty baseline:
# the check goal writes each module's pmd.xml with the CURRENT baseline already subtracted, so
# regenerating from those reports records only the violations the old baseline missed. The
# existing file is therefore emptied before the analysis runs.
#
# Usage (from anywhere):
#     ./scripts/generate-pmd-baseline.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT/quorumtrading-backend"

OUT="pmd-baseline.properties"

echo "Clearing the existing baseline so the analysis reports every violation..."
: > "$OUT"

echo "Building and analysing all modules (violations not failing the build)..."
./mvnw -q -DskipTests verify -Dpmd.failOnViolation=false

shopt -s nullglob
reports=(quorumtrading-*/target/pmd.xml)
if [[ ${#reports[@]} -eq 0 ]]; then
    echo "::error::No PMD reports were produced." >&2
    exit 1
fi
echo "Reading ${#reports[@]} module reports..."

python3 - "$OUT" "${reports[@]}" <<'PY'
import collections, sys, xml.etree.ElementTree as ET

out_path, *reports = sys.argv[1:]
NS = "{http://pmd.sourceforge.net/report/2.0.0}"

by_class = collections.defaultdict(set)
total = 0
for report in reports:
    root = ET.parse(report).getroot()
    for f in root.iter(f"{NS}file"):
        for v in f.iter(f"{NS}violation"):
            pkg = (v.get("package") or "").strip()
            cls = (v.get("class") or "").strip()
            rule = v.get("rule")
            if not cls or not rule:
                continue
            # The plugin matches on the fully qualified OUTER class name.
            fqcn = f"{pkg}.{cls}" if pkg else cls
            by_class[fqcn].add(rule)
            total += 1

with open(out_path, "w") as fh:
    fh.write("# PMD baseline — GENERATED, do not hand-edit.\n")
    fh.write("#\n")
    fh.write("# Every violation present when this was generated, so the gate is green on arrival\n")
    fh.write("# and only NEW violations fail the build. Regenerate with\n")
    fh.write("# scripts/generate-pmd-baseline.sh after FIXING violations; adding entries to\n")
    fh.write("# absorb new ones defeats the gate and will show up in the diff.\n")
    fh.write("#\n")
    fh.write(f"# {total} violations across {len(by_class)} classes.\n")
    for fqcn in sorted(by_class):
        fh.write(f"{fqcn}={','.join(sorted(by_class[fqcn]))}\n")

print(f"{total} violations across {len(by_class)} classes -> {out_path}")
PY
