#!/usr/bin/env python

import os
import sys
import pymongo
import socket

import time

import requests

from bson.objectid import ObjectId


# TODO: Support whitelist for service accounts?


# XXX: Set the desired script operation
fixmongo = True
verbose = False
showok = False
promptempty = True
promptinactive = True
promptactive = True


# XXX: Target a particular instance of Clowder/Mongo
clowder_url = 'http://localhost:9000/'
clowder_secret_key = ''
client = pymongo.MongoClient('mongodb://localhost:27017/clowder')
clowder = client['clowder']
#clowder = client['clowder-dev']


# List all collections to check/migrate
# NOTE: 'apikeys' does not use the same convention
#    for id, and is handled separately
collections = [
    "spaces.projects",
    "uploads",
    "collections",
    "folders",
    "datasets",
    "metadata",
    "comments",
]

# Map from collection name to its representation in our aggregated result
user_fields = {
    "spaces.projects": "spaces",
    "uploads": "uploads",
    "collections": "collections",
    "folders": "folders",
    "datasets": "datasets",
    "metadata": "metadata",
    "comments": "comments",
}

# Map from collection name to its id field
id_map = {
    "spaces.projects": "creator",
    "uploads": "author._id",
    "collections": "author._id",
    "folders": "author._id",
    "datasets": "author._id",
    "metadata": "creator._id",
    "comments": "author._id",
}

def main():
    global clowder, verbose, showok 

    # Check for and report on any userpass accounts
    results = check(verbose=verbose, showok=showok)

    # Print results
    print('\nUser check complete!')
    print('Results:    %d / %d / %d (EMPTY / INACTIVE / ACTIVE)' % (len(results['empty']), len(results['inactive']), len(results['active'])))

    # If user wants to fix mongo, loop through results and fix them
    if fixmongo:
        summary = fix(results)
        if len(results['empty']) > 0 or len(results['inactive']):
            print('Summary: %d/%d empty, %d/%d inactive accounts purged\n' % (summary['empty'], len(results['empty']), summary['inactive'], len(results['inactive'])))

def print_user(user, verbose=False, message=None):
    print(str(user['_id']))
    print("\tuserId        : " + user['identityId']['userId'])
    print("\tproviderId    : " + user['identityId']['providerId'])
    print("\ttotal created : " + str(user['total_created']))
    if verbose:
        print("\tuploads       : " + str(len(user['uploads'])))
        print("\tcollections   : " + str(len(user['collections'])))
        print("\tdatasets      : " + str(len(user['datasets'])))
        print("\tcomments      : " + str(len(user['comments'])))
        print("\tevents_to     : " + str(len(user['events_to'])))
        print("\tevents_from   : " + str(len(user['events_from'])))
        print("\tmetadata      : " + str(len(user['metadata'])))
        print("\tfolders       : " + str(len(user['folders'])))
        print("\tspaces        : " + str(len(user['spaces'])))
        print("\tapikeys       : " + str(len(user['apikeys'])))
        #print("\tevents        : " + str(len(user['events'])))
    if message is not None:
        print('\tmessage       : ' + str(message))
       
def prompt_for_new_id():
    """Ask the user to enter the new id of the user. The answer
       can be used to reassociate their old entries to a new user.
    """

    prompt = '    Please enter the new id for this user: '
    try:
        while True:
            sys.stdout.write(prompt)
            new_id = raw_input().lower()
            if new_id is not None and new_id != '':
                return new_id
            else:
                sys.stdout.write("Please enter a valid ObjectId string\n")
    except KeyboardInterrupt as e:
        print('\nKeyboardInterrupt detected: aborting...\n')
        exit(1)

def query_yes_no(question, default="yes"):
    """Ask a yes/no question via raw_input() and return their answer.

    "question" is a string that is presented to the user.
    "default" is the presumed answer if the user just hits <Enter>.
        It must be "yes" (the default), "no" or None (meaning
        an answer is required of the user).

    The "answer" return value is True for "yes" or False for "no".
    """
    valid = {"yes": True, "y": True, "ye": True,
             "no": False, "n": False}
    if default is None:
        prompt = " [y/n] "
    elif default == "yes":
        prompt = " [Y/n] "
    elif default == "no":
        prompt = " [y/N] "
    else:
        raise ValueError("invalid default answer: '%s'" % default)

    try:
        while True:
            sys.stdout.write(question + prompt)
            choice = raw_input().lower()
            if default is not None and choice == '':
                return valid[default]
            elif choice in valid:
                return valid[choice]
            else:
                sys.stdout.write("Please respond with 'yes' or 'no' "
                                 "(or 'y' or 'n').\n")
    except KeyboardInterrupt as e:
        print('\nKeyboardInterrupt detected: aborting...\n')
        exit(1)

def userIsUserpass(user):
    if user['identityId']['providerId'] == 'userpass':
        return True
    else:
        return False

def userIsCilogon(user):
    if user['identityId']['providerId'] == 'cilogon':
        return True
    else:
        return False

def userHasCreatedObjects(user):
    if user['total_created'] > 0:
        return True
    else:
        return False

def userHasLoggedIn(user):
    # Some users were active before "lastLogin" was added as a persisted field
    if userHasCreatedObjects(user):
        return True
    if user['lastLogin'] is not None and user['lastLogin'] != "N / A":
        return True
    else:
        return False

def check(verbose=False, showok=False):
    global clowder, duplicates, fixmongo

    results = { 'active': [], 'inactive': [], 'empty': [] }
    for user in aggregate_users(): 
        if userIsUserpass(user):
            if not userHasCreatedObjects(user):
                print_user(user, verbose, 'Found EMPTY userpass account')
                results['empty'].append(user)
            elif not userHasLoggedIn(user):
                print_user(user, verbose, 'Found INACTIVE userpass account')
                results['inactive'].append(user)
            else:
                results['active'].append(user)
                print_user(user, verbose, 'Found ACTIVE userpass account')
        elif userIsCilogon(user) and showok:
            print_user(user, verbose, 'Skipping cilogon user')
            continue
    return results


def fix(results):
    deleted_inactive_users = 0
    deleted_empty_users = 0

    if len(results['empty']) > 0:
        print('\n-----------------------------------------------------------\n')
        print('Purging EMPTY user accounts:')
        for user in results['empty']:
            if len(user['apikeys']) > 0:
                print('    ~~~ WARNING: User %s (%s) has %d apikeys associated with their account. Proceed with caution! ~~~' % (user['_id'], user['email'], len(user['apikeys'])))
                time.sleep(2)
            # Prompt for confirmation
            if not promptempty or (promptempty and query_yes_no('Delete user %d/%d: %s (%s)?' % (results['empty'].index(user) + 1, len(results['empty']), user['_id'], user['email']), default='yes')):
                if verbose:
                    print('Purging EMPTY user account: ' + str(user['_id']))
                if delete_user(user):
                    deleted_empty_users += 1
        print('EMPTY user accounts purged successfully')

    if len(results['inactive']) > 0:
        print('\n-----------------------------------------------------------\n')
        print('Cycling through INACTIVE user accounts to prompt for removal:')
        for user in results['inactive']:
            # Prompt for confirmation
            if not promptinactive or (promptinactive and query_yes_no('Delete this user? ' + str(user), default='no')):
                if verbose:
                    print('Removing INACTIVE user account: ' + str(user['_id']))
                if delete_user(user):
                    deleted_inactive_users += 1
        print('Unwanted INACTIVE user accounts have been removed')
    print('\n-----------------------------------------------------------\n')

    # Warn about necessary manual migration
    print('Cycling through ACTIVE user accounts to prompt for manual migration')
    for user in results['active']:
        print('Migration needed for %s' % (user['_id']))

        possible_aliases = clowder['social.users'].find({ 'email': user['email'], 'identityId.providerId': 'cilogon' })
        aliases = [{ '_id': alias['_id'], 'identityId': alias['identityId'] } for alias in possible_aliases]
        print('    Possible cilogon aliases:')
        for alias in aliases:
            print('      - %s: %s' % (alias['_id'], alias['identityId']))
        if len(aliases) == 0:
            print('      - N / A')

        # Prompt for new_user_id
        if promptactive and query_yes_no('Migrate user %s (%s)?' % (user['_id'], user['email']), default='no'):
            new_id = prompt_for_new_id()
            migrate_user(user, new_id)

    print('\n')
    return { 'empty': deleted_empty_users, 'inactive': deleted_inactive_users }

# Deletes a user and all their resources from the database
def delete_user(user):
    global clowder, collections

    # TODO: Delete uploads using Clowder API to be more thorough
    # Delete user data from all collections
    #for collection in collections:
    #    if len(user[user_fields[collection]]) > 0:
    #        query = {}
    #        query[id_map[collection]] = user['_id']
    #        result = clowder[collection].delete_many(query)
    #        print('Deleted %s/%s entries for %s from %s' % (result.deleted_count, len(user[user_fields[collection]]), user['_id'], collection))
    #
    #        if result.deleted_count < len(user[user_fields[collection]]):
    #            print('FAILED to delete user %s: one or more entries failed to delete from %s' % (user['_id'], collection))
    #            return False

    # Delete all files via Clowder API to clean up thumbnails, metadata, etc
    #for file in user['uploads']:
    #    # TODO: Delete file via Clowder API
    #    response = requests.delete(clowder_url + 'api/files/' + file['_id'], params={'key': clowder_secret_key})
    #    print('Attempting to delete file %s from Clowder: %s' % (file['_id'], r.text))


    # Delete apikeys
    if len(user['apikeys']) > 0:
        result = clowder['users.apikey'].delete_many({ 'identityId.providerId': user['identityId']['providerId'], 'identityId.userId': user['identityId']['userId'] })
        print('Deleted %s/%s entries for %s from %s' % (result.deleted_count, len(user['apikeys']), user['_id'], 'users.apikey'))

        if result.deleted_count < len(user['apikeys']):
            print('FAILED to delete user %s: one or more apikeys failed to delete' % user['_id'])
            return False

    # Delete from social.users
    result = clowder['social.users'].delete_one({ '_id': ObjectId(user['_id']) })
    print('User deleted successfully: ' + str(user['_id']))

    return result.deleted_count > 0


# Some places have an author field that is a "MiniUser"
# This method will update such references with a new owner
# NOTE: Make sure these stay in-sync
def update_author(owner_id, collection, new_author):
    global clowder

    # TODO: Prompt for confirmation?

    query = {}
    query[id_map[collection]] = user['_id']
    set_fields = {}
    set_fields[id_map[collection]] = new_author
    result = clowder[collection].update_many(query, { "$set": set_fields })
    print('Migrated %s/%s documents from %s to %s in %s' % (result.modified_count, result.matched_count, owner_id, new_author['_id'], collection))


# Converts all resources owned by 'user._id' to instead be owned by 'new_id'
def migrate_user(user, new_id):
    global clowder, collections

    # TODO: Prompt for confirmation?

    # Update user data in all collections to be owned by new_id
    for collection in collections:
        query = {}
        query[id_map[collection]] = user['_id']
        set_fields = {}
        set_fields[id_map[collection]] = ObjectId(new_id)
        result = clowder[collection].update_many(query, { "$set": set_fields }) 
        print('Migrated %s/%s documents from %s to %s in %s' % (result.modified_count, result.matched_count, user['_id'], new_id, collection))

    # Lookup new user to retrieve their identityId / apikeys
    new_user = clowder['social.users'].find_one({ '_id': ObjectId(new_id) })

    # Migrate this user's apikeys
    result = clowder['users.apikey'].update_many(
        # Lookup by old user identityId
        { "identityId.userId": user['identityId']['userId'], "identityId.providerId": user['identityId']['providerId'] },
        # Update them to match the new user's identityId
        { "$set": { "identityId.userId": new_user['identityId']['userId'], "identityId.providerId": new_user['identityId']['providerId'] }}
    )
    if result.matched_count > 0 and result.modified_count > 0:
        print('Migrated %s/%s documents from %s to %s in %s' % (result.modified_count, result.matched_count, user['_id'], new_id, 'users.apikey'))

    # Migrate user's space permissions
    for spacerole in user['spaceandrole']:
        if spacerole not in new_user['spaceandrole']:
            new_user['spaceandrole'].append(spacerole)

    # TODO: Confirm success?

    print('User %s was migrated successfully to %s' % (user['_id'], new_id))

    # TODO: Prompt for confirmation?
    # Delete the old entry from social.users
    #if user['_id'] != new_id:
    #    if not delete_user(user):
    #        print('Failed to delete user %s' % user['_id'])

    return True

# A long query to join our user collection with the resources owned by that user
def aggregate_users():
    global clowder
    
    pipeline = [
        {"$lookup": { "from": "social.users", "localField": "email", "foreignField": "email", "as": "user_aliases" }},
        {"$lookup": { "from": "spaces.projects", "localField": "_id", "foreignField": "creator", "as": "user_spaces" }},
        {"$lookup": { "from": "collections", "localField": "_id", "foreignField": "author._id", "as": "user_collections" }},
        {"$lookup": { "from": "datasets", "localField": "_id", "foreignField": "author._id", "as": "user_datasets" }},
        {"$lookup": { "from": "folders", "localField": "_id", "foreignField": "author._id", "as": "user_folders" }},
        {"$lookup": { "from": "uploads", "localField": "_id", "foreignField": "author._id", "as": "user_uploads" }},
        {"$lookup": { "from": "comments", "localField": "_id", "foreignField": "author._id", "as": "user_comments" }},
        {"$lookup": { "from": "events_to", "localField": "_id", "foreignField": "targetuser._id", "as": "user_events_to" }},
        {"$lookup": { "from": "events_from", "localField": "_id", "foreignField": "user._id", "as": "user_events_from" }},
        {"$lookup": { "from": "metadata", "localField": "_id", "foreignField": "creator._id", "as": "user_metadata" }},
        {"$lookup": { "from": "users.apikey", "localField": "identityId.userId", "foreignField": "identityId.userId", "as": "user_apikeys" }},
        {"$project": {
            # Preserve some essential fields from each user
            "_id": 1,
            "email": 1,
            "identityId.providerId": 1,
            "identityId.userId": 1,
            "lastLogin": { "$ifNull": [ "$lastLogin", "N / A" ] }, 

            # Preserve all space permissions so they can be migrated
            "spaceandrole": 1,

            # Save all of our joined values from above?
            "aliases": { "$filter": { "input" : { "$map":  { "input": "$user_aliases", "as": "user", "in": "$$user.identityId" } }, "as": "identity", "cond": { "$eq": [ "$$identity.providerId", "cilogon" ] } } },
            "uploads":  "$user_uploads", 
            "collections":  "$user_collections",
            "datasets":  "$user_datasets",
            "events_from": "$user_events_from",
            "events_to": "$user_events_to",
            "folders":  "$user_folders",
            "spaces":  "$user_spaces",
            "comments":  "$user_comments",
            "metadata":  "$user_metadata",
            "apikeys": {"$filter": { "input": "$user_apikeys", "as": "key", "cond": { "$eq": [ "$$key.identityId.providerId", "$$ROOT.identityId.providerId" ]  }  } },

            # Sum the total objects created
            "total_created": { "$sum": [ 
                { "$size": "$user_uploads" }, 
                { "$size": "$user_collections" },  
                { "$size": "$user_spaces" },  
                { "$size": "$user_datasets"},  
                { "$size": "$user_folders" },  
                #{ "$size": "$user_apikeys" },       # Don't count apikeys as created objects
                { "$size": "$user_comments" },  
                #{ "$size": "$user_events_to" },     # Safe to ignore events_to for now
                #{ "$size": "$user_events_from" },   # Safe to ignore events_from for now
                { "$size": "$user_metadata" },
            ]},
        }},
    ]

    return list(clowder['social.users'].aggregate(pipeline))

# ----------------------------------------------------------------------
# start of program
# ----------------------------------------------------------------------
if __name__ == "__main__":
    main()

