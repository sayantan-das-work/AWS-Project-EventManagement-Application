#!/bin/bash
# Builds the CodeDeploy bundle (tar.gz) from this module. Run from anywhere:
#   deploy/make-bundle.sh [output-file]        (default: target/usermgmt-bundle.tar.gz)
# Contents: appspec.yml, deploy/, pom.xml, src/ (the jar is built on the instance, so no target/).
# Every file is packed with mode 755 so the hook scripts are executable even when the checkout
# comes from Windows, where git does not record the executable bit.
set -euo pipefail

cd "$(dirname "$0")/.."
OUT="${1:-target/usermgmt-bundle.tar.gz}"
mkdir -p "$(dirname "$OUT")"

for required in appspec.yml deploy/scripts/after_install.sh pom.xml src; do
  [ -e "$required" ] || { echo "ERROR: $required not found" >&2; exit 1; }
done

tar --mode=755 --owner=0 --group=0 -czf "$OUT" appspec.yml deploy pom.xml src
# A bundle must not contain the bundle itself or build output
echo "Created $OUT"
tar -tzf "$OUT" | head -50
