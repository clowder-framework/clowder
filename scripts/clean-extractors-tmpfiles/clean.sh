#!/bin/bash

LIST=$1

while IFS=, read -r path days;
do
  case "$path" in \#*) continue ;; esac
  echo "path: $path", "days: $days"
  deleted=$(/usr/bin/find $path -maxdepth 1 -mindepth 1 -mtime +$days)
  echo $deleted
  rm -rf $deleted
  echo "deletion done"
done < "$LIST"

