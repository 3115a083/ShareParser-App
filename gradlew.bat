@echo off
if exist "%~dp0gradle\wrapper\gradle-wrapper.jar" (
  java -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) else (
  echo gradle-wrapper.jar is not vendored. Use the Gradle version declared in gradle\wrapper\gradle-wrapper.properties directly.
  exit /b 1
)
