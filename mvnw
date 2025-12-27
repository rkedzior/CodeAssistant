#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper startup script, version 0.5.6
# ----------------------------------------------------------------------------

set -euf

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

MAVEN_PROJECTBASEDIR="$BASE_DIR"
export MAVEN_PROJECTBASEDIR

WRAPPER_DIR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"
WRAPPER_URL="https://repo.maven.apache.org/maven2/io/takari/maven-wrapper/0.5.6/maven-wrapper-0.5.6.jar"

if [ ! -r "$WRAPPER_JAR" ]; then
  mkdir -p "$WRAPPER_DIR"
  echo "Downloading Maven Wrapper..."
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR"
  else
    echo "ERROR: curl or wget is required to download the Maven Wrapper." 1>&2
    exit 1
  fi
fi

exec java -classpath "$WRAPPER_JAR" "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" org.apache.maven.wrapper.MavenWrapperMain "$@"

