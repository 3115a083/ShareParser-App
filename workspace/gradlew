#!/bin/sh
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
echo "gradle-wrapper.jar is not vendored in this initial checkpoint. Use Gradle 8.13 directly: gradle $*" >&2
exit 1
