set SCRIPT_DIR=%~dp0
java -Xms1024M -Xmx2048M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=1024M -jar "%SCRIPT_DIR%sbt-launch.jar" %*
