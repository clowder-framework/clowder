#!/bin/bash

VERSION="1.19.2"

sed -i~ "s#^  val version = \".*\"\$#  val version = \"${VERSION}\"#" project/Build.scala
sed -i~ "s#^  version: .*\$#  version: ${VERSION}#" public/swagger.yml
sed -i~ "s#^release = '.*'\$#release = '${VERSION}'#" doc/src/sphinx/conf.py
sed -i~ "s/^##.*unreleased.*$/## ${VERSION} - $(date +'%Y-%m-%d')/i" CHANGELOG.md
