#!/bin/zsh
# Headless self-test runbook: build, boot the dev server, wait for the suite verdict, clean up.
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home}
./gradlew build --console=plain -q
rm -rf run/world  # fresh world: builtin data packs apply at creation
LOG=$(mktemp -t mythstack-selftest)
./gradlew runServer --console=plain > "$LOG" 2>&1 &
GPID=$!
n=0
until grep -qE "ALL CHECKS PASSED|CHECK\(S\) FAILED|BUILD FAILED" "$LOG" 2>/dev/null; do
  sleep 2; n=$((n+1))
  if [ $n -gt 240 ]; then echo "TIMEOUT ($LOG)"; break; fi
  kill -0 $GPID 2>/dev/null || { echo "GRADLE EXITED EARLY ($LOG)"; break; }
done
grep -E "\[selftest\]" "$LOG" | grep -E "FAIL|PASSED|FAILED" || true
echo "PASS count: $(grep -cE 'selftest\] PASS' "$LOG" || true)"
echo "data errors: $(grep -cE "Couldn't parse|Failed to load" "$LOG" || true)"
"$JAVA_HOME/bin/jps" -l | grep -i knot | awk '{print $1}' | xargs kill 2>/dev/null || true
kill $GPID 2>/dev/null || true
python3 scripts/audit_assets.py
grep -qE "ALL CHECKS PASSED" "$LOG"
