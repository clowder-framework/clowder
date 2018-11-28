#!/usr/bin/env python
#
# This script will loop through all entries in the `uploads` 
# collection in MongoDB. For each entry, the `loader` value 
# determines what type of storage backing the file has:
# either MongoDB or Disk.

# The `loader_id` tells us where the file should be located 
# and, depending on the value for `loader`, is either an id 
# in MongoDB or a path to a file on disk.
#
#
# Script Parameters
# (NOTE: The following have not been exposed via CLI)
#   * `orphans`: if True, search the filesystem for orphaned 
#                files, which no longer exist in MongoDB 
#                (default: `False`)
#   * `fixmongo`: If True, attempt to repair the entries in 
#                 MongoDB where possible (default: `False`)
#   * `verbose`: If True, print additional details for any 
#                invalid entries encountered (default: `True`)
#   * `showok`: If True, print additional details for all 
#               entries, including valid ones (default: `False`)
#   * `duplicates`: If True, report any duplicate `loader_id` 
#                   encountered (default: `False`)
#
# Steps Checked
#   * If `loader` is empty, the script will attempt to check 
#     the filesystem for the file. If `loader_id` is located
#     in `/tmp`, we attempt to copy the file to a more permanent
#     location and update the entry in `uploads`.
#   * If `loader` is MongoDB, we must do a second lookup in 
#     `uploads.files` to make sure that the bytes for that file 
#     are still in storage.
#   * If `loader` is Disk, we check the filesystem at the path 
#     pointed to by `loader_id`.
#
#
# Appendix: Verbose Messages List
#   * `file is where it should be`: this entry is valid. You can 
#     suppress this message by setting `showok` to False.
#   * `found a file in new location`: File was moved out of `/tmp`
#      folder and is now ok
#   * `duplicate loader_id`: this `loader_id` has been encountered
#     before. You can suppress this message by setting `duplicates`
#     to False.
#   * `file not found`: Invalid entry found in `uploads`. Delete 
#     this entry by setting `fixmongo` to True
#   * `no loader specified, file found on disk`: We found a blank 
#     `loader`, but we noticed that the file bytes are on disk.
#     Delete this entry by setting `fixmongo` to True
#   * `no loader specified, file not on disk`: We found a blank 
#     `loader`, and we can't find the file bytes associated with it
#   * `no loader_id specified`: `loader` was MongoDB, but we didn't
#     have a `loader_id`, so there is nothing we could lookup here
#
#


import os
import pymongo
import socket

from bson.objectid import ObjectId


# XXX: Set the desired script operation
orphans = False
duplicates = False
fixmongo = False
verbose = True
showok = False


# XXX: Set your paths on disk
TMP_DIR = '/tmp/clowder'
CLOWDER_DIR = '/home/clowder'
DATA_DIR = os.path.join(CLOWDER_DIR, 'data')


# XXX: Target a particular instance of Clowder/Mongo
url = 'http://localhost:9000/'
client = pymongo.MongoClient('mongodb://localhost:27017/clowder')
clowder = client['clowder']
#clowder = client['clowder-dev']


def main():
    global clowder, verbose, showok, url

    # TODO: Should we also check tiles/textures/geometriesi/previews/thumbnails?
    check('uploads', url, prefix="files/", verbose=verbose, showok=showok)

    if orphans:
        check_for_orphan_files()

def find_dataset(id):
    global clowder

    datasets = list()
    for datasetobj in clowder['datasets'].find({"files": id}):
        datasets.append(datasetobj)
    for folderobj in clowder['folders'].find({"files": id}):
        for datasetobj in clowder['datasets'].find({"_id": folderobj['parentDatasetId']}):
            datasets.append(datasetobj)
    return datasets


def print_upload(upload, url, prefix):
    print(str(upload['_id']))
    if url:
        print("\turl      : " + url + prefix + str(upload['_id']))
    if "loader" in upload:
        print("\tloader   : " + upload['loader'])
    else:
        print("\tloader   : NOT FOUND")
    if "loader_id" in upload:
        print("\tloader_id: " + upload['loader_id'])
    else:
        print("\tloader_id: NOT FOUND")
    if "filename" in upload:
        print("\tfilename : " + str(upload['filename'].encode('utf-8')))
    if "size" in upload:
        print("\tsize     : " + str(upload['length']))
    if "status" in upload:
        print("\tstatus   : " + upload['status'])
    if "uploadDate" in upload:
        print("\tdate     : " + str(upload['uploadDate']))
    if "author" in upload:
        print("\tauthor   : " + upload['author']['fullName'])
    if "file_id" in upload:
        print("\tfile_id  : " + str(upload['file_id']))


def check(collection, url=None, prefix=None, verbose=False, showok=False):
    global clowder, duplicates, fixmongo

    if verbose:
        print("")
        print("================================================================================")
        print(collection.upper())
        print("================================================================================")

    loader_ids = list()
    for upload in clowder[collection].find().sort("_id"):
        printed = False
        if "loader" not in upload or upload['loader'] == '':
            id = str(upload['_id'])
            newpath = DATA_DIR + "%s/%s/%s/%s/%s" % (collection, id[-2:], id[-4:-2], id[-6:-4], id)
            upload['loader'] = 'services.filesystem.DiskByteStorageService'
            upload['loader_id'] = newpath
            if verbose:
                printed = True
                print_upload(upload, url, prefix)
                if os.path.exists(upload['loader_id']):
                    print('\tmessage  : no loader specified, file found on disk')
                else:
                    print('\tmessage  : no loader specified, file not on disk')
            if fixmongo:
                clowder[collection].update({'_id': upload['_id']},
                                           {'$set': {'loader': 'services.filesystem.DiskByteStorageService',
                                                     'loader_id': newpath}},
                                           upsert=False, multi=False)

            if upload['loader_id'].startswith("/tmp"):
                oldpath = upload['loader_id']
                newpath = oldpath.replace(TMP_DIR, DATA_DIR)
                if fixmongo:
                    clowder[collection].update({'_id': upload['_id']}, {'$set': {'loader_id': newpath}},
                                               upsert=False, multi=False)
                    upload['loader_id'] = newpath
                if os.path.exists(newpath):
                    if verbose:
                        printed = True
                        print_upload(upload, url, prefix)
                        print('\tmessage  : found a file in new location')
                elif os.path.exists(oldpath):
                    if verbose:
                        printed = True
                        print_upload(upload, url, prefix)
                        print('\tmessage  : cp %s %s' % (oldpath, newpath))
                else:
                    if verbose:
                        printed = True
                        print_upload(upload, url, prefix)
                        print('\tmessage  : file not found')
                    # TODO: Should we delete here too? Or just notify the user of bad state?
            elif not os.path.exists(upload['loader_id']):
                if verbose:
                    printed = True
                    print_upload(upload, url, prefix)
                    print('\tmessage  : file not found')
                if fixmongo:
                    # TODO: Ask for confirmation first?
                    print('Deleting invalid upload: ' + str(upload['_id']))
                    clowder[collection].delete_one({'_id': upload['_id']})
            else:
                if verbose and showok:
                    printed = True
                    print_upload(upload, url, prefix)
                    print('\tmessage  : file is where it should be')
        elif upload['loader'] == 'services.mongodb.MongoDBByteStorage':
            if "loader_id" not in upload:
                if verbose:
                    printed = True
                    print_upload(upload, url, prefix)
                    print('\tmessage  : no loader_id specified')
                continue

            file = clowder[collection + ".files"].find_one({ '_id': ObjectId(upload['loader_id']) })
            if file is None:
                if verbose:
                    printed = True
                    print_upload(upload, url, prefix)
                    print('\tmessage  : file not found') 
                if fixmongo:
                    # TODO: Ask for confirmation first?
                    print('Deleting invalid upload: ' + str(upload['_id']))
                    clowder[collection].delete_one({'_id': upload['_id']})
            elif verbose and showok:
                printed = True
                print_upload(upload, url, prefix)
                print('\tmessage  : file is where it should be')
        elif upload['loader'] == 'services.filesystem.DiskByteStorageService':
            if not os.path.exists(upload['loader_id']):
                if verbose:
                    printed = True
                    print_upload(upload, url, prefix)
                    print('\tmessage  : file not found')
                if fixmongo:
                    # TODO: Ask for confirmation first?
                    print('Deleting invalid upload: ' + str(upload['_id']))
                    clowder[collection].delete_one({'_id': upload['_id']})
            elif verbose and showok:
                printed = True
                print_upload(upload, url, prefix)
                print('\tmessage  : file is where it should be')

        if duplicates:
            if upload['loader_id'] in loader_ids:
                if not printed:
                    printed = True
                    print_upload(upload, url, prefix)
                print('\tmessage  : duplicate loader_id')
            else:
                loader_ids.append(upload['loader_id'])

# "Borrowed" from StackOverflow
# See https://stackoverflow.com/questions/1094841/reusable-library-to-get-human-readable-version-of-file-size
def sizeof_fmt(num, suffix='B'):
    for unit in ['','Ki','Mi','Gi','Ti','Pi','Ei','Zi']:
        if abs(num) < 1024.0:
            return "%3.1f%s%s" % (num, unit, suffix)
        num /= 1024.0
    return "%.1f%s%s" % (num, 'Yi', suffix)


def check_for_orphan_files():
    global clowder, DATA_DIR

    print('Checking for orphan files...')
    orphans = []
    for folder, subs, files in os.walk(os.path.join(DATA_DIR, 'uploads')):
        for filename in files:
            # Look up file iby ID in mongo.. filename should be ID
            mongo_file = clowder['uploads'].find_one({ 'loader_id': os.path.join(folder, filename) })

            # Found a file: everything is good
            if mongo_file is not None:
                if verbose and showok:
                    print('Found file in Mongo: ' + str(mongo_file['_id']))
            else:
                if verbose:
                    print('File found on disk, but not in Mongo: ' + os.path.join(folder, filename))
                orphans.append(os.path.join(folder, filename))

    print('\nFound ' + str(len(orphans)) + ' orphaned files:')
    for filepath in orphans:
        filesize = os.stat(filepath).st_size
        print(' - ' + filepath + '\t' + str(filesize) + '\t(' + str(sizeof_fmt(filesize) + ')'))


# ----------------------------------------------------------------------
# start of program
# ----------------------------------------------------------------------
if __name__ == "__main__":
    main()

