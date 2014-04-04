set SCRIPT_DIR=%~dp0
java -Dfile.separator=\/ -Xms1024M -Xmx2048M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=1024M -jar "%SCRIPT_DIR%sbt-launch.jar" %*
