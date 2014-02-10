#!/bin/bash

# Usage: $0 <object_type> [ <object_id> ]
#   where object_type is one of: file, dataset, section.
#   object_id is optional, which contains the OID to use,
#   instead of let the script find one that does not have any existing tags.
# Example:
#   ./test-tag-generic.sh file
#   ./test-tag-generic.sh section
#   ./test-tag-generic.sh dataset 52697b5ae4b008632f496995
# Purpose:
#   Find a file/dataset/section that does not have any existing tags,
#   or use one with a given ID, and test the various REST endpoints
#   with the following tests:
#   Positive tests:
#       get/add/remove some/remove all tags, and
#   Negative tests:
#       get tags for an invalid ObjectId -- not hexi number ("hello")
#       get tags for an invalid ObjectId -- hexi number, but invalid (having one less digit)
#       get tags for a valid ObjectId, but the ID is not found for the given object type.
# Notes:
#   This script uses the "mongo-exec.sh" script.  Its source is located
#   in the same source directory as this one.  Please make sure it is in
#   your PATH.
# Author: Rui Liu.
# Date:   Nov 21, 2013.

# To debug this script, pleaes uncomment the next line.
#set -x
OTYPE=$1
echo Object type = $OTYPE.
OID=""
if [ $# == 2 ] ; then
  OID=$2
  echo Given object ID = $OID.
fi

# Use mongo directly to find one that does not have the "tags" field, or it's empty,
# so as not to change existing data.
if [ x$OID == "x" ] ; then
  DB_COL="${OTYPE}s"
  if [ $OTYPE == "file" ] ; then
    DB_COL="uploads.files"
  fi
  MARG="db.${DB_COL}.findOne({ \\\\\$or: [ { tags: { \\\\\$exists: false } }, { tags: [] } ] }, { _id: 1 })"
  OID=`mongo-exec.sh "$MARG" | cut -d\" -f4`
fi
echo ${OTYPE} ID: ${OID}
if [ $OID == "null" ] ; then
  echo The object ID is null, can not use it to test. Exiting...
  exit 2
fi

CMD="curl -H Content-Type:application/json"
URL_HEAD="http://localhost:9000"
COMM_KEY="?key=r1ek3rs"
GET_TAGS_CMD="$CMD ${URL_HEAD}/api/${OTYPE}s/${OID}/tags${COMM_KEY}"

# Original commands:
# Remove all tags:
#curl -H 'Content-Type: application/json' -d '{"tags":["amituofo"]}' "http://localhost:9000/api/${OTYPE}s/${OID}/tags/remove_all?key=r1ek3rs"
# Get tags
#curl -H 'Content-Type: application/json' "http://localhost:9000/api/${OTYPE}s/${OID}/tags?key=r1ek3rs"
# Add tags
#curl -H 'Content-Type: application/json' -d '{"tags":[" amituofo\t ", " \t \t Hello  \t\tworld!\t", "  buddha ", "statue"]}' "http://localhost:9000/api/${OTYPE}s/${OID}/tags?key=r1ek3rs"
# Remove some tags
#curl -H 'Content-Type: application/json' -d '{"tags":["  statue ", "      buddha\t\t  "]}' "http://localhost:9000/api/${OTYPE}s/${OID}/tags/remove?key=r1ek3rs"
# Remove all tags
#curl -H 'Content-Type: application/json' -d '{"tags":["amituofo"]}' "http://localhost:9000/api/${OTYPE}s/${OID}/tags/remove_all?key=r1ek3rs"

# syntax: get <REST_ENDPOINT>
function get {
  $CMD ${URL_HEAD}"$1"${COMM_KEY}
}
# syntax: post <POST_DATA> <REST_ENDPOINT>
function post {
  $CMD -d "$1" ${URL_HEAD}"$2"${COMM_KEY}
}
# syntax: test_post_tag <test_name> <POST_DATA> <REST_ENDPOINT>
function test_post_tag {
  echo "-------------------------------------------"
  echo "$1"
  post "$2" "$3"
  echo ; echo Get tags ; $GET_TAGS_CMD ; echo
}
# syntax: test_get <test_name> <REST_ENDPOINT>
function test_get {
  echo "-------------------------------------------"
  echo "$1"
  get "$2"
  echo
}

test_post_tag 'Remove all tags' '{"tags":["amituofo"]}' "/api/${OTYPE}s/${OID}/tags/remove_all"

test_post_tag 'Add tags' '{"tags":[" amituofo\t ", " \t \t Hello  \t\tworld!\t", "  buddha ", "statue"], "extractor_id": "curl"}' "/api/${OTYPE}s/${OID}/tags"

test_post_tag 'Remove some tags' '{"tags":["  statue ", "      buddha\t\t  "], "extractor_id": "curl"}' "/api/${OTYPE}s/${OID}/tags/remove"

test_post_tag 'Remove all tags' '{"tags":["amituofo"]}' "/api/${OTYPE}s/${OID}/tags/remove_all"

test_get 'Invalid ObjectId: non-digit' "/api/${OTYPE}s/hello/tags"
test_get 'Invalid ObjectId: digit, but invalid' "/api/${OTYPE}s/5272d0d7e4b0c4c9a43e81c/tags"
test_get 'Valid ObjectId, not found' "/api/${OTYPE}s/5272d0d7e4b0c4c9a43e81c8/tags"

