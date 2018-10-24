db.app.configuration.update({"key" : "countof.users"}, {$set: { "value": NumberLong(db.social.users.count())}})
db.app.configuration.update({"key" : "countof.datasets"}, {$set: { "value": NumberLong(db.datasets.count())}})
db.app.configuration.update({"key" : "countof.files"}, {$set: { "value": NumberLong(db.uploads.count())}})
db.app.configuration.update({"key" : "countof.bytes"}, {$set: { "value": db.uploads.aggregate({$group: {_id: "bytes", "sum": {$sum : "$length"}}}).next().sum}})
db.app.configuration.update({"key" : "countof.collections"}, {$set: { "value": NumberLong(db.collections.count())}})
db.app.configuration.update({"key" : "countof.spaces"}, {$set: { "value": NumberLong(db.spaces.projects.count())}})

