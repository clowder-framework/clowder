# Script to migrate all files on disk to AWS S3 buckets.


The script will scan Clowder collections: 'logo', 'uploads', 'thumbnails', 'titles', 'textures', 'previews'. And then upload files on disk to AWS S3 buckets and update `loader` to `services.s3.S3ByteStorageService` in db. And this script can be rerun multiple times, all the successfully migrated disk files will not be migrated to S3 bucket again.

## Build the docker image
```
docker build -t migratefilestos3 .
```

## Run as docker



### Environment `CLOWDER_PREFIX` and `CLOWDER_UPLOAD`

`CLOWDER_PREFIX` specifies the Clowder disk space path, which can be found inside Clowder configuration file. When `CLOWDER_PREFIX` is given, e.g., `/home/clowder/data`, the script will truncate the loader_id starting with `CLOWDER_PREFIX` and use the rest of suffix as the filepath on S3 bucket. For example, the filepath (loader_id) `/home/clowder/data/upload/38/5c/75/5dd4783d77c838271f755c38` will be uploaded to S3 bucket as `/upload/38/5c/75/5dd4783d77c838271f755c38`.



`CLOWDER_UPLOAD` specifies the Clowder mounting folder path, e.g., `/incoming`. When `CLOWDER_UPLOAD` is given, the script will truncate the loader_id which starts with `CLOWDER_UPLOAD` and use the concatenation of `/s3/uploads/` and the rest of suffix as the filepath on S3 bucket. For example, the filepath (loader_id) `/incoming/apple/image.jpg` will be uploaded to S3 bucket as `/s3/uploads/apple/image.jpg`.


### mounting path

Clowder disk files reside on the host filesystem, thus users need to mount the data folders from host filesystem into the running container. For example, this mount `-v /home/clowder/data:/home/clowder/data` will let docker container to access the files under the folder `/home/clowder/data` inside docker container.


There is an example to migrate Clowder disk storage files to S3 bucket.
```
docker run -it --rm --env SERVICE_ENDPOINT=http://s3service_endpoint --env BUCKET=localbucket --env AWS_ACCESS_KEY_ID=yourawsid --env AWS_SECRET_ACCESS_KEY=yourawssecretkey --env REGION=us-east-1 --env DBURL=mongodb://mongodbpublicip --env DBNAME=clowder --env OUTPUTFOLDER=/output -v /home/clowder/data:/home/clowder/data --env CLOWDER_PREFIX=/home/clowder/data -v ${PWD}/output:/output migratefilestos3
```

## Run as python script

Run the below command. You do not have to specify the host filesystem, since the script can access the files.

```
python ./main.py --dburl mongodb://mongodbpublicip:27017 --dbname clowder --s3endpoint s3-endpoint --s3bucket s3bucketname --s3ID yourawsid --s3KEY yourawssecretkey --s3REGION s3region --outputfolder ./output --clowderprefix /home/clowder/data
```

## Test with minIO
Before migrating files to AWS S3 bucket, you can use minIO to simulate the S3 bucket in your local machine.

To start minIO, just run the below command to start the minIO docker container on your machine. In the terminal, minIO will print out service endpoint, access secrets, region, etc. You can use the access secrets to login minIO on the machine and setup the bucket.

```
docker run --name=minio -dit -p 8000:9000 -v $(pwd)/minio-buckets:/data minio/minio server /data --compat && docker logs -f minio
```

## Copy Clowder Database
It is safe to operate the script on the copy of database.
First, dump the Clowder database `clowder` into a archive file.

```
mongodump --archive="clowder-migration-test-db" --db=clowder
```
Then, restore the archive file into a new database which is called `clowder-migration-test-db`.

```
mongorestore --archive="clowder-migration-test-db" --nsFrom='clowder.*' --nsTo='clowder-migration-test-db.*'
```
