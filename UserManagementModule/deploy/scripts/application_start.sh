#!/bin/bash
# ApplicationStart: enable the service at boot and (re)start it.
set -euo pipefail

systemctl enable usermgmt
systemctl restart usermgmt
echo "usermgmt service started"
