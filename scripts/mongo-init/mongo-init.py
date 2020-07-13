#!/usr/bin/env python

import datetime
import os
import sys
import uuid

import pymongo
from passlib.hash import bcrypt

mongo_uri = os.getenv('MONGO_URI', 'mongodb://localhost:27017/clowder')
user_name = os.getenv('USERNAME', '')
user_email = os.getenv('EMAIL_ADDRESS', '')
user_firstname = os.getenv('FIRST_NAME', '')
user_lastname = os.getenv('LAST_NAME', '')
user_password = os.getenv('PASSWORD', '')
user_admin = os.getenv('ADMIN', '')

# check values
if not user_email:
    if sys.stdin.isatty():
        user_email = input('EMAIL_ADDRESS : ')
    if not user_email:
        print("Need to specify EMAIL_ADDRESS")
        sys.exit(-1)
if not user_name:
    user_name = user_email
if not user_firstname:
    if sys.stdin.isatty():
        user_firstname = input('FIRST_NAME    : ')
    if not user_firstname:
        print("Need to specify FIRST_NAME")
        sys.exit(-1)
if not user_lastname:
    if sys.stdin.isatty():
        user_lastname = input('LAST_NAME     : ')
    if not user_lastname:
        print("Need to specify LAST_NAME")
        sys.exit(-1)
if not user_password:
    if sys.stdin.isatty():
        user_password = input('PASSWORD.     : ')
    else:
        user_password = str(uuid.uuid4()).replace('-', '')
        print('PASSWORD      : ' + user_password)
    if not user_password:
        print("Need to specify PASSWORD")
        sys.exit(-1)
if not user_admin:
    if sys.stdin.isatty():
        user_admin = input('ADMIN         : ')
    if not user_admin:
        print("Need to specify ADMIN")
        sys.exit(-1)
user_admin = user_admin.lower() == 'true'

# connect to mongo
client = pymongo.MongoClient(mongo_uri)
dbase = client.get_default_database()
users = dbase.get_collection('social.users')

# check if user already exists
if users.find_one({"identityId.userId": user_name, "identityId.providerId": "userpass"}):
    print("USER ALREADY EXISTS, will not create user")
    sys.exit(0)

# generate password if not specified
encrypted_password = "$2a" + bcrypt.hash(user_password, rounds=10)[3:]

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

result = dbase['app.configuration'].update_one({"key": "countof.users"}, {"$inc": {"value": 1}})
if not result.acknowledged:
    print("ERROR updating user count")
else:
    print("Updated user count (%d match, %d updated)" % (result.matched_count, result.modified_count))

# Done
print("Inserted new user with id=%s" % user_name)
