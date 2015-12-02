/***

This script finds all files that are currently not part of any dataset and puts them under a new dataset with the same name and authorship as the file.
To run this script either copy and paste the following code after choosing the needed database in MongoDB shell or by running the following command from a Unix shell:

mongo <host>:27017/<database> assign_datasets.js

In the command, replace:
<host> by the host name where the database is running.
<database> by the name of the database where this update should happen.

***/

//Find all datasets with files
var allDatasetsCursor = db.datasets.find({},{_id: 0, files: 1});
var allFilesHavingDatasets = [];

// Get all files which are contained in a dataset
while (allDatasetsCursor.hasNext()) {
	var dataset = allDatasetsCursor.next();

	for (var i = 0; i < dataset.files.length; i++) {

		allFilesHavingDatasets.push(dataset.files[i])
	}
}

// Get all files that are not contained in a dataset
var allIndependentFilesCursor = db.uploads.files.find(
	{ 
		_id: 
		{
			$nin: allFilesHavingDatasets
		}
	});

// Create new datasets for independent files and attach the file to the created dataset
while (allIndependentFilesCursor.hasNext()) { 

	var file = allIndependentFilesCursor.next();
	printjson(file["_id"]);

	var datasetDoc = 
	{
		"_typeHint" : "models.Dataset",
		"author" : file["author"], // assigning authorship to dataset
		"collections" : [ ],
		/*
			Above field can be updated if the newly created dataset should belong to one or more existing collections. 
			Just add the ObjectIds of the collection(s) to this array. Please note that this will not update the dataset count in the document for collections in MongoDB. 
			It will need to be updated using an update query.
		*/
		"created" : new ISODate(),
		"datasetXmlMetadata" : [ ],
		"description" : "",
		"files" : [
			file["_id"] // adding the file to the dataset
		],
		"metadata" : { },
		"name" : file["filename"], // assigning dataset name 
		"spaces" : [ ],
		/*
			Above field can be updated if the newly created dataset should belong to one or more existing spaces. 
			Just add the ObjectIds of the space(s) to this array. Please note that this will not update the dataset count in the document for spaces in MongoDB. 
			It will need to be updated using an update query.
		*/
		"streams_id" : [ ],
		"tags" : [ ],
		"userMetadata" : { }
	};

	db.datasets.insert(datasetDoc);
}