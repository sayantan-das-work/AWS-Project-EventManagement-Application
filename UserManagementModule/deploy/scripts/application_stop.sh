#!/bin/bash
# ApplicationStop: stop the service if it is running. Safe on the very first deployment.
set -uo pipefail

if systemctl is-active --quiet usermgmt 2>/dev/null; then
  echo "Stopping usermgmt service"
  systemctl stop usermgmt
else
  echo "usermgmt service not running; nothing to stop"
fi
exit 0
