set SCRIPT_DIR=%~dp0
java -Xms1024M -Xmx1024M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=1024M -jar "%SCRIPT_DIR%sbt-launch.jar" %*
