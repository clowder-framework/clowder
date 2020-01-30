import os
import argparse
import pymongo
from bson import ObjectId
from pymongo.mongo_client import MongoClient
import traceback
from datetime import datetime

from s3 import S3Bucket


def print_to_logfile(f, reason, collection_name, mongo_id, loader_id, s3_path):
    f.write("%s\t%s\t%s\t%s\t%s\n" % (reason, collection_name, mongo_id, loader_id, s3_path))


def get_s3_path(loader_id, clowder_upload_folder, clowder_prefix_folder):
    s3_filepath = None
    if clowder_upload_folder:
        if loader_id.startswith(clowder_upload_folder):
            s3_filepath = "/s3/uploads" + loader_id[len(clowder_upload_folder):]
    if clowder_prefix_folder:
        if loader_id.startswith(clowder_prefix_folder):
            s3_filepath = loader_id[len(clowder_prefix_folder):]
    return s3_filepath


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='migrate Clowder files to S3')

    parser.add_argument('--dburl', '-u', default=os.getenv("DBURL", None),
                        help='Clowder databse url')

    parser.add_argument('--dbname', '-d', default=os.getenv("DBNAME", None),
                        help='Clowder databse name')

    parser.add_argument('--clowderupload', '-l', default=os.getenv("CLOWDER_UPLOAD", None),
                    help='the mounted folder contains the uploaded files, e.g., /incoming')

    parser.add_argument('--clowderprefix', '-e', default=os.getenv("CLOWDER_PREFIX", None),
                    help='clowder disk space path, e.g., /generated/data')

    parser.add_argument('--outputfolder', '-o', default=os.getenv("OUTPUTFOLDER", None),
                        help='the output folder where a file contains the information'
                             ' of a list of all files that have been migrated to S3')

    parser.add_argument('--s3endpoint', '-s', default=os.getenv("SERVICE_ENDPOINT", None),
                        help='S3 service endpoint')

    parser.add_argument('--s3bucket', '-b', default=os.getenv("BUCKET", None),
                        help='S3 bucket name')

    parser.add_argument('--s3ID', '-i', default=os.getenv("AWS_ACCESS_KEY_ID", None),
                        help='S3 aws access key id')

    parser.add_argument('--s3KEY', '-k', default=os.getenv("AWS_SECRET_ACCESS_KEY", None),
                    help='S3 aws access secret key')

    parser.add_argument('--s3REGION', '-r', default=os.getenv("REGION", None),
                    help='S3 region')

    args = parser.parse_args()

    print('migrate disk storage files to s3')
    print('Clowder dburl: %s, dbname: %s' % (args.dburl, args.dbname))
    print('upload files to S3: region: %s, service endpoint: %s' % (args.s3REGION, args.s3endpoint))
    print('S3 bucket: %s' % args.s3bucket)
    print("Clowder Upload folder: %s, diskstorage folder: %s" % (args.clowderupload, args.clowderprefix))
    f = None
    total_bytes_uploaded = 0
    collections = ['logo', 'uploads', 'thumbnails', 'titles', 'textures', 'previews']
    try:
        s3bucket = S3Bucket(args.s3bucket, args.s3endpoint, args.s3ID, args.s3KEY, args.s3REGION)
        now = datetime.now()
        dt_string = now.strftime("%d-%m-%YT%H:%M:%S")
        file_path = "%s/migrates-filelist-%s.txt" % (args.outputfolder, dt_string)
        directory = os.path.dirname(file_path)
        if not os.path.exists(directory):
            os.mkdir(directory)
        f = open(file_path, "w")
        client = MongoClient(args.dburl)
        db = client.get_database(name=args.dbname)

        for collection in collections:
            try:
                num = db[collection].count_documents({})
                num_not_disk_storage = 0
                ndiskfiles = 0
                nfails = 0
                nsuccess = 0
                for data_tuple in db[collection].find({}, {'_id': 1, 'loader_id': 1, 'loader': 1}):
                    s3_path = ""
                    try:
                        record_id = str(data_tuple.get('_id'))
                        file_bytes = 0
                        loader = data_tuple.get('loader')
                        if loader == 'services.filesystem.DiskByteStorageService':
                            ndiskfiles += 1
                            loader_id = data_tuple.get('loader_id')

                            s3_path = get_s3_path(loader_id, args.clowderupload, args.clowderprefix)
                            try:
                                statinfo = os.stat(loader_id)
                                file_bytes = statinfo.st_size
                            except Exception as ex:
                                # cannnot access the file by loader_id, either permission or not exist.
                                print_to_logfile(f, "missing", collection, record_id, loader_id, "")
                            s3bucket.upload(loader_id, s3_path)
                            # update record loader to 'services.s3.S3ByteStorageService'
                            update_data = dict()
                            update_data['loader'] = 'services.s3.S3ByteStorageService'
                            update_data['loader_id'] = s3_path
                            status = db[collection].update_one({'_id': ObjectId(record_id)}, {"$set": update_data})
                            if status.modified_count != 1:
                                raise Exception("failed to update db %d" % record_id)

                            nsuccess += 1
                            print_to_logfile(f, "success", collection, record_id, loader_id, s3_path)
                        else:
                            num_not_disk_storage += 1
                    except Exception as ex:
                        # failed, either failed to upload to S3 bucket or update local db
                        print_to_logfile(f, "failed", collection, record_id, loader_id, s3_path)
                        nfails += 1
                    total_bytes_uploaded += file_bytes
            except Exception as ex:
                # traceback.print_exc()
                pass
            print("completed on collection: %s, total records: %d, total on disk files %d, success: %d, failed: %d" %
                  (collection, num, ndiskfiles, nsuccess, nfails))
    except Exception as ex:
        # traceback.print_exc()
        pass
    finally:
        if f:
            f.close()

    print("upload total bytes: " + str(total_bytes_uploaded))
    print("Done")

