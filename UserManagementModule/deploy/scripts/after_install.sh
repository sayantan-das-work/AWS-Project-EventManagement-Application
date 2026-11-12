#!/bin/bash
# AfterInstall: build the shaded jar with Maven (downloads all dependencies), swap it in as
# /opt/usermgmt/app.jar and lock down permissions. If the build fails the previous jar is left
# untouched and the old version is started again, then the deployment fails.
set -euo pipefail
# shellcheck source=common.sh
. "$(dirname "$0")/common.sh"

APP_DIR=/opt/usermgmt
SRC_DIR=$APP_DIR/source
export HOME="${HOME:-/root}"                 # Maven cache lives in $HOME/.m2 and is reused between deployments
export MAVEN_OPTS="-Xmx512m"

on_failure() {
  echo "ERROR: build/install failed" >&2
  if [ -f "$APP_DIR/app.jar" ]; then
    echo "Restarting the previous version" >&2
    systemctl daemon-reload || true
    systemctl start usermgmt || true
  fi
}
trap on_failure ERR

resolve_java
resolve_mvn

cd "$SRC_DIR"
# Unit tests run in CI; skip them here so a deployment does not depend on test timing
"$MVN_BIN" -B --no-transfer-progress -DskipTests clean package

# The shaded jar is user-management-module-<version>.jar; skip original-*.jar (unshaded, does not
# match) and any leftover *-shaded.jar copy
NEW_JAR=$(ls -t target/user-management-module-*.jar | grep -v -e '-shaded\.jar$' | head -1)
if [ -z "$NEW_JAR" ]; then
  echo "ERROR: build produced no application jar" >&2
  exit 1
fi
install -m 640 -o root -g usermgmt "$NEW_JAR" "$APP_DIR/app.jar.new"
mv -f "$APP_DIR/app.jar.new" "$APP_DIR/app.jar"

# Run the service with the same Java that built it (systemd drop-in overrides ExecStart)
mkdir -p /etc/systemd/system/usermgmt.service.d
cat > /etc/systemd/system/usermgmt.service.d/java.conf <<EOF
[Service]
ExecStart=
ExecStart=$JAVA_BIN -jar $APP_DIR/app.jar
EOF
chmod 644 /etc/systemd/system/usermgmt.service.d/java.conf

# The service user may read the jar and the env file; nobody else may read the env file
chown root:usermgmt "$APP_DIR"
chmod 750 "$APP_DIR"
chown root:usermgmt /etc/usermgmt/usermgmt.env
chmod 640 /etc/usermgmt/usermgmt.env
chmod 644 /etc/systemd/system/usermgmt.service

systemctl daemon-reload
echo "Built and installed $(basename "$NEW_JAR") (Java: $JAVA_BIN)"
