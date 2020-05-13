#!/bin/sh

# This script is designed to test functionality of data uploading & extraction.
# Creates a dataset and upload files, submit for extraction to ncsa.file.digest, verify result, delete.

# Clowder URL and key to use as well as file can be defined below.
# Needs to have 'jq' installed.

CLOWDER_URL=${CLOWDER_URL:-""}
CLOWDER_KEY=${CLOWDER_KEY:-""}
TARGET_FILE=${TARGET_FILE:-""}

# Slack token for notifications
SLACK_TOKEN=${SLACK_TOKEN:-""}
SLACK_CHANNEL=${SLACK_CHANNEL:-"#github"}
SLACK_USER=${SLACK_USER:-"NCSA Build"}


post_message() {
  printf "$1\n"
  if [ "${SLACK_TOKEN}" != "" -a "${SLACK_CHANNEL}" != "" ]; then
    url="https://hooks.slack.com/services/${SLACK_TOKEN}"
    txt=$( printf "$1\n" | tr '\n' "\\n" | sed 's/"/\\"/g' )
    payload="payload={\"channel\": \"${SLACK_CHANNEL}\", \"username\": \"${SLACK_USER}\", \"text\": \"${txt}\"}"
    result=$(curl -s -X POST --data-urlencode "${payload}" $url)
  fi
}

# ------------------------ Create dataset ------------------------
DATASET_ID=$(curl -s -X POST -H "Content-Type: application/json" \
    -d '{"name":"Temporary Test Dataset", "description":"Created automatically by test script."}' \
    $CLOWDER_URL/api/datasets/createempty?key=$CLOWDER_KEY)
DATASET_ID=$(echo $DATASET_ID  | jq '.id' | sed s/\"//g)
echo "Dataset ID: $DATASET_ID"

# ------------------------ Upload file ------------------------
FILE_ID=$(curl -X POST -F File=@$TARGET_FILE \
    $CLOWDER_URL/api/uploadToDataset/$DATASET_ID?key=$CLOWDER_KEY&extract=0)
FILE_ID=$(echo $FILE_ID | jq '.id' | sed s/\"//g)
echo "File ID: $FILE_ID"

# Validate upload
FILE_UPLOADED=0
RETRIES=0
while [ $FILE_UPLOADED = 0 ]; do
    RESPONSE=$(curl -X GET -H "Content-Type: application/json" \
        $CLOWDER_URL/api/files/$FILE_ID/metadata?key=$CLOWDER_KEY)
    RESPONSE=$(echo $RESPONSE | jq '.status' | sed s/\"//g)
    if [ "$RESPONSE" = "PROCESSED" ]; then
        FILE_UPLOADED=1
    fi
    RETRIES=$((RETRIES+1))
    if [ $RETRIES = 12 ]; then
      echo "File upload not PROCESSED after 2 minutes. There may be a problem. Deleting dataset."
      curl -X DELETE $CLOWDER_URL/api/datasets/$DATASET_ID?key=$CLOWDER_KEY
      post_message "Upload+extract test script failing on $CLOWDER_URL\/files\/$FILE_ID (status is not PROCESSED)"
      exit 1
    fi
    echo "File upload not complete; checking again in 10 seconds."
    sleep 10
done
echo "File upload complete."

# ------------------------ Submit for extraction ------------------------
curl -X POST -H "Content-Type: application/json" \
    -d '{"extractor": "ncsa.file.digest"}' \
    $CLOWDER_URL/api/files/$FILE_ID/extractions?key=$CLOWDER_KEY

# Validate extraction
FILE_EXTRACTED=0
RETRIES=0
while [ $FILE_EXTRACTED -eq 0 ]; do
    RESPONSE=$(curl -X GET -H "Content-Type: application/json" \
        $CLOWDER_URL/api/extractions/$FILE_ID/status?key=$CLOWDER_KEY)
    echo $RESPONSE
    RESPONSE=$(echo $RESPONSE | jq '."ncsa.file.digest"' | sed s/\"//g)
    if [ "$RESPONSE" = "DONE" ]; then
        FILE_EXTRACTED=1
        post_message "Extractor: [ncsa.file.digest] success $CLOWDER_URL/files/$FILE_ID"
    fi
    RETRIES=$((RETRIES+1))
    if [ $RETRIES = 24 ]; then
      echo "File extraction not DONE after 4 minutes. There may be a problem. Deleting dataset."
      curl -X DELETE $CLOWDER_URL/api/datasets/$DATASET_ID?key=$CLOWDER_KEY
      post_message "Upload+extract test script failing on $CLOWDER_URL/files/$FILE_ID (extractor not DONE)"
      exit 1
    fi
    echo "File extraction not complete; checking again in 10 seconds."
    sleep 10
done
echo "File extraction complete."
    

# ------------------------ Delete dataset ------------------------
curl -X DELETE $CLOWDER_URL/api/datasets/$DATASET_ID?key=$CLOWDER_KEY

echo "Test complete."