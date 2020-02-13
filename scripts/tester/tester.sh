#!/bin/sh

# This script is designed to test functionality of data uploading & extraction.
# Creates a dataset and upload files, submit for extraction to ncsa.file.digest, verify result, delete.

# Clowder URL and key to use as well as file can be defined below.
# Needs to have 'jq' installed.

CLOWDER_URL=
CLOWDER_KEY=
TARGET_FILE=



# Create dataset
DATASET_ID=$(curl -X POST -H "Content-Type: application/json" \
	-d '{"name":"Temporary Test Dataset", "description":"Created automatically by test script."}' \
	$CLOWDER_URL/api/datasets/createempty?key=$CLOWDER_KEY)
echo $DATASET_ID
DATASET_ID=$(echo $DATASET_ID  | jq '.id' | sed s/\"//g)
echo "Dataset ID: $DATASET_ID"


# Upload file
FILE_ID=$(curl -X POST -H "Content-Type: application/json" \
	-F File=@$TARGET_FILE \
	$CLOWDER_URL/api/uploadToDataset/$DATASET_ID?key=$CLOWDER_KEY&extract=0)
FILE_ID=$(echo $FILE_ID | jq '.id' | sed s/\"//g)
echo "File ID: $FILE_ID"

# Validate upload
FILE_UPLOADED=0
while [ $FILE_UPLOADED = 0 ]; do
	RESPONSE=$(curl -X GET -H "Content-Type: application/json" \
		$CLOWDER_URL/api/files/$FILE_ID/metadata?key=$CLOWDER_KEY)
	RESPONSE=$(echo $RESPONSE | jq '.status' | sed s/\"//g)
	if [ "$RESPONSE" = "PROCESSED" ]; then
		FILE_UPLOADED=1
	fi
	echo "File upload not complete."
	sleep 5
done
echo "File upload complete."


# Submit for extraction
curl -X POST -H "Content-Type: application/json" \
	-d '{"extractor": "ncsa.file.digest"}' \
	$CLOWDER_URL/api/files/$FILE_ID/extractions?key=$CLOWDER_KEY

# Validate extraction
FILE_EXTRACTED=0
while [ FILE_EXTRACTED = 0 ]; do
	RESPONSE=$(curl -X GET -H "Content-Type: application/json" \
		$CLOWDER_URL/api/extractions/$FILE_ID/status?key=$CLOWDER_KEY)
	RESPONSE=$(echo $RESPONSE | jq '.ncsa.file.digest' | sed s/\"//g)
	if [ "$RESPONSE" = "DONE" ]; then
		FILE_EXTRACTED=1
	fi
	echo "File extraction not complete."
	sleep 5
done
echo "File extraction complete."
	

# Delete dataset
curl -X DELETE $CLOWDER_URL/api/datasets/$DATASET_ID?key=$CLOWDER_KEY
RESPONSE=$(curl -X GET $CLOWDER_URL/api/datasets/$DATASET_ID/metadata?key=$CLOWDER_KEY)

echo "Test complete."
