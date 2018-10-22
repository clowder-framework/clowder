# add key to prevent updates from happening
db.app.configuration.update({"key" : "mongodb.updates"}, { $addToSet: {"value": "update-avatar-url-to-https"}})

# update all collections
var count=0;
db.social.users.find({"avatarURL": /^http:\/\/www.gravatar.com\//}).forEach(function(d) {
	d.avatarURL=d.avatarURL.replace("http://www.gravatar.com/","https://www.gravatar.com/"); 
	db.social.users.save(d);
	count++;
});
print("users : " + count);
count=0;
db.events.find({"user.avatarURL": /^http:\/\/www.gravatar.com\//}).forEach(function(d) {
	d.user.avatarURL=d.user.avatarURL.replace("http://www.gravatar.com/","https://www.gravatar.com/"); 
	db.events.save(d);
	count++;
});
print("events creator : " + count);
count=0;
db.events.find({"targetuser.avatarURL": /^http:\/\/www.gravatar.com\//}).forEach(function(d) {
	d.targetuser.avatarURL=d.targetuser.avatarURL.replace("http://www.gravatar.com/","https://www.gravatar.com/"); 
	db.events.save(d);
	count++;
});
print("events target : " + count);
['datasets', 'uploads', 'comments', 'curationObjects', 'folders'].forEach(function(x) { 
	count=0;
	db[x].find({"author.avatarURL": /^http:\/\/www.gravatar.com\//}).forEach(function(d) {
		d.author.avatarURL=d.author.avatarURL.replace("http://www.gravatar.com/","https://www.gravatar.com/"); 
		db[x].save(d);
		count++;
	});
	print(x + " : " + count);
});
