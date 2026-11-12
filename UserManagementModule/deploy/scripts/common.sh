#!/bin/bash
# Shared helpers, sourced by the other hook scripts. JDK 17+ and Maven are pre-installed on the
# instance; these functions only locate them and fail with a clear message if they are missing.
# CodeDeploy hooks run with a minimal PATH, so common install locations are searched too.

# Sets JAVA_BIN (real path of java) and JAVA_HOME; requires a JDK (javac present) of version 17+.
resolve_java() {
  local candidate="" real major
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    candidate="$JAVA_HOME/bin/java"
  elif command -v java >/dev/null 2>&1; then
    candidate="$(command -v java)"
  else
    for dir in /usr/lib/jvm/*; do
      [ -x "$dir/bin/java" ] && candidate="$dir/bin/java" && break
    done
  fi
  if [ -z "$candidate" ]; then
    echo "ERROR: java not found. Install JDK 17 on the instance." >&2
    return 1
  fi
  real="$(readlink -f "$candidate")"
  major="$("$real" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
  if [ -z "$major" ] || [ "$major" -lt 17 ]; then
    echo "ERROR: Java 17 or newer required, found '${major:-unknown}' at $real" >&2
    return 1
  fi
  if [ ! -x "$(dirname "$real")/javac" ]; then
    echo "ERROR: $real is a JRE without javac; a full JDK is needed to build" >&2
    return 1
  fi
  JAVA_BIN="$real"
  JAVA_HOME="$(dirname "$(dirname "$real")")"
  export JAVA_HOME
}

# Sets MVN_BIN to the Maven executable.
resolve_mvn() {
  local candidate=""
  if command -v mvn >/dev/null 2>&1; then
    candidate="$(command -v mvn)"
  else
    for path in /opt/maven/bin/mvn /usr/local/bin/mvn /usr/share/maven/bin/mvn /opt/apache-maven-*/bin/mvn; do
      [ -x "$path" ] && candidate="$path" && break
    done
  fi
  if [ -z "$candidate" ]; then
    echo "ERROR: mvn not found. Install Maven on the instance." >&2
    return 1
  fi
  MVN_BIN="$candidate"
}
