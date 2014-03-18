#!/bin/sh

#java -Xms1024M -Xmx2048M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=1024M -XX:ReservedCodeCacheSize=128M -jar `dirname $0`/sbt-launch.jar "$@"

java -Xss1M -Xmx2G -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=384M -Dsbt.log.noformat=true ${JAVA_OPTS} -jar `dirname $0`/sbt-launch.jar "$@"