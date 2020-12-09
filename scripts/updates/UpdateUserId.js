/***
This code iterates through each document where user_id does not exist and checks if file_id exists
in uploads.files collection. If a file exists, it grabs the author._id for use as user_id. If it
does not exist (or if author._id is null), it searches for the file_id in the datasets collection.
If found, it gets the author._id for use as the user_id. If an author id is found and not null
an update to the extractions collection is made by adding user_id: author._id. 
***/

// To filter by time, include another clause in find eg: "start": {"$gte": ISODate("2020-01-01T00:00:00Z")}
db.extractions.find({"user_id":{$exists: 0}}).forEach(function(ext) {
    let authorID = null;
    // Looping through each extraction where user_id doesn't exist,
    // if file_id found in uploads.files, get author._id
    let foundFile = db.uploads.files.findOne({"_id": ext.file_id});
    if (foundFile != null) {
        authorID = foundFile.author._id;
    } else {
        let foundFile = db.uploads.findOne({"_id": ext.file_id});
        if (foundFile != null) {
            authorID = foundFile.author._id;
        }
    }

    // If file not found in uploads.files or if author._id doesn't exist,
    // look up file_id in datasets, get author.id if found
    if (foundFile == null || authorID == null) {
        let foundAuthorInDatasets = db.datasets.findOne({"files": {$in: [ext.file_id]}});
        if (foundAuthorInDatasets != null) {
            authorID = foundAuthorInDatasets.author._id;
        }
    }
    if (authorID != null) {
        // If job_id exists update author._id for all documents with job_id,
        // else update based on the current document id.
        if (ext.job_id != null) {
            // Update user_id for entry in extractions database
            db.extractions.update({"job_id": ext.job_id}, {
                "$set": {
                    "user_id": authorID
                }
            });
        }
        else {
            // Update user_id for entry in extractions database
            db.extractions.update({"_id": ext._id}, {
                "$set": {
                    "user_id": authorID
                }
            });
        }
    }
});
