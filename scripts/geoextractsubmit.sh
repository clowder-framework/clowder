#!/bin/bash

MONGO_URI="mongodb://127.0.0.1:27017/clowder"
APIKEY=adminKey
extractorname=extractorNAME
CLOWDERHOST=clowderhost

echo submit files to $extractorname on $CLOWDERHOST

fileids=$(mongo $MONGO_URI --eval 'db.metadata.distinct("attachedTo._id", {"content.WMS Layer Name": {$exists: true}}, {"attachedTo._id": 1, "_id": 0 }).forEach(function(doc){print(doc.valueOf())})')

for fileid in "${fileids[@]}"
do
    echo submit $fileid
    curl -d '{"extractor": "'$extractorname'"}' -H "Content-Type: application/json" -X POST $CLOWDERHOST/clowder/api/files/$fileid/extractions?key=$APIKEY
done

