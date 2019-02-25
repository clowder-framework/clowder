#!/usr/bin/env python

import datetime
import os
import sys
import uuid

import pymongo
import pymongo.results
from passlib.hash import bcrypt

mongo_uri = os.getenv('MONGO_URI', 'mongodb://localhost:27017/clowder')
user_name = os.getenv('USERNAME', '')
user_email = os.getenv('EMAIL_ADDRESS', '')
user_firstname = os.getenv('FIRST_NAME', '')
user_lastname = os.getenv('LAST_NAME', '')
user_password = os.getenv('PASSWORD', '')
user_admin = os.getenv('ADMIN', 'false').lower() == 'true'

# use email if not explicitly set username
if not user_name:
    if not user_email:
        print("Need to specify USERNAME or EMAIL_ADDRESS")
        os.exit(-1)
    user_name = user_email

# make sure to have firstname/lastname
if not user_firstname:
    user_firstname = "Example"
if not user_lastname:
    user_lastname = "User"

# connect to mongo
client = pymongo.MongoClient(mongo_uri)
dbase = client.get_default_database()
users = dbase.get_collection('social.users')

# check if user already exists
if users.find_one({"identityId.userId": user_name, "identityId.providerId": "userpass"}):
    print("USER ALREADY EXISTS, will not create user")
    sys.exit(-1)

# generate password if not specified
if not user_password:
    user_password = str(uuid.uuid4()).replace('-', '')
    print("GENERATED PASSWORD == " + user_password)
encrypted_password = "$2a" + bcrypt.encrypt(user_password, rounds=10)[3:]

# create document that will be inserted
user_document = {
  "identityId": {
    "userId": user_name,
    "providerId": "userpass"
  },
  "_typeHint": "models.ClowderUser", 
  "firstName": user_firstname,
  "lastName": user_lastname,
  "fullName": user_firstname + " " + user_lastname,
  "email": user_email,
  "authMethod": {
    "_typeHint": "securesocial.core.AuthenticationMethod", 
    "method": "userPassword"
  },
  "passwordInfo": {
    "_typeHint": "securesocial.core.PasswordInfo", 
    "hasher": "bcrypt", 
    "password": encrypted_password
  },
  "admin": False,
  "serverAdmin": False,
  "status": "Active",
  "termsOfServices": {
    "accepted": True,
    "acceptedDate": datetime.datetime.now(),
    "acceptedVersion": "2016-06-06",
  }
}

if user_admin:
    user_document['admin'] = True
    user_document['serverAdmin'] = True
    user_document['status'] = 'Admin'

# insert user in mongo
result = users.insert_one(user_document)
if not result.acknowledged:
    print("ERROR inserting")
else:
    print("Inserted user (id=%s)" % result.inserted_id)

result = dbase['app.configuration'].update_one({"key": "countof.users"}, {"$inc": { "value": 1}})
if not result.acknowledged:
    print("ERROR updating user count")
else:
    print("Updated user count (%d match, %d updated)" % (result.matched_count, result.modified_count))

# Done
print("Inserted new user with id=%s" % user_name)