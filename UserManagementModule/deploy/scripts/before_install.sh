#!/bin/bash
# BeforeInstall: check the pre-installed JDK 17 and Maven, create the service user and folders,
# and clear the previous source tree. Nothing is installed here.
set -euo pipefail
# shellcheck source=common.sh
. "$(dirname "$0")/common.sh"

resolve_java
resolve_mvn
echo "Using Java at $JAVA_BIN"
echo "Using Maven at $MVN_BIN"

if ! id usermgmt >/dev/null 2>&1; then
  echo "Creating service user usermgmt"
  useradd --system --no-create-home --shell /sbin/nologin usermgmt
fi

mkdir -p /opt/usermgmt /etc/usermgmt
# Remove the old source so files deleted from git do not linger and get compiled
rm -rf /opt/usermgmt/source
