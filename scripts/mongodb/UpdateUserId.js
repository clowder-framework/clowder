/***
This code iterates through each document where user_id does not exist, checks if file_id exists
in uploads.files collection. If it exists, it grabs the author._id for use as user_id. If it
does not exist (or if author._id is null), it searches for the file_id in the datasets collection.
If found, it gets the author._id for use as the user_id. If an author id is found and not null
an update to the extractions collection is made by adding user_id: author._id. 
***/

db.extractions.find({"user_id":{$exists: 0}}).forEach(function(ext) {
    let authorID = null;
    // Looping through each extraction where user_id doesn't exist...
    // Look up file_id in uploads.files, get author.id if found
    let foundFile = db.uploads.files.findOne({"_id": ext.file_id})
    if (foundFile != null) {
        authorID = foundFile.author._id;
    }

    // if not found in uploads.files, look up file_id in datasets, get author.id if found
    if (foundFile == null || authorID == null) {
        let foundAuthorInDatasets = db.datasets.findOne({"files": {$in: [ext.file_id]}});
        if (foundAuthorInDatasets != null) {
            authorID = foundAuthorInDatasets.author._id;
        }
    }
    if (authorID != null) {
        // update user_id for entry in extractions database
        db.extractions.update({"_id": ext._id}, {
            "$set": {
                "user_id": authorID
            }
        });
    }
});
