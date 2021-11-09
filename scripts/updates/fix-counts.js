db.app.configuration.update({"key" : "mongodb.updates"}, { $addToSet: {"value": "update-space-bytes"}})
db.spaces.projects.update({"spaceBytes": {$exists : false}}, {$set: {'spaceBytes': NumberLong(0)}})
db.app.configuration.update({"key" : "mongodb.updates"}, { $addToSet: {"value": "update-space-files"}})
db.spaces.projects.update({"fileCount": {$exists : false}}, {$set: {'fileCount': 0}})

db.app.configuration.update({"key" : "countof.users"}, {$set: { "value": NumberLong(db.social.users.count())}})
db.app.configuration.update({"key" : "countof.datasets"}, {$set: { "value": NumberLong(db.datasets.count())}})
db.app.configuration.update({"key" : "countof.files"}, {$set: { "value": NumberLong(db.uploads.count())}})
db.app.configuration.update({"key" : "countof.bytes"}, {$set: { "value": db.uploads.aggregate({$group: {_id: "bytes", "sum": {$sum : "$length"}}}).next().sum}})
db.app.configuration.update({"key" : "countof.collections"}, {$set: { "value": NumberLong(db.collections.count())}})
db.app.configuration.update({"key" : "countof.spaces"}, {$set: { "value": NumberLong(db.spaces.projects.count())}})

db.spaces.projects.find().sort({"datasetCount": 1}).forEach(function(s) {
	var spaceBytes = 0;
	var filecount = 0;
	db.datasets.find({"spaces": s._id}).forEach(function(d) {
		d.files.forEach(function(fid) {
			db.uploads.find({"_id": fid}).forEach(function(f) {
				spaceBytes += f.length;
				filecount += 1;
			});
		});
	});
	s.spaceBytes = NumberLong(spaceBytes);
	s.fileCount = filecount;
	db.spaces.projects.save(s);
	print(s["_id"] + " " + s.name + " = " + s.spaceBytes + " in " + s.fileCount + " files");
})
