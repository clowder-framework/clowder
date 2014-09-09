set SCRIPT_DIR=%~dp0

java -Xms1024M -Xmx1024M -Xss1M -XX:MaxPermSize=128M -XX:+CMSClassUnloadingEnabled -jar "%SCRIPT_DIR%\sbt-launch.jar" %*
