import boto3
import traceback
from s3transfer.manager import TransferManager


class S3Bucket:
    def __init__(self, bucket, endpoint, id, key, region):
        self.bucket = bucket
        self.service_endpoint = endpoint
        self.aws_access_key_id = id
        self.aws_secret_access_key = key
        self.region_name = region
        self.client = boto3.client('s3',
                                   endpoint_url=self.service_endpoint,
                                   aws_access_key_id=self.aws_access_key_id,
                                   aws_secret_access_key=self.aws_secret_access_key, region_name=self.region_name)
        self.transfer = TransferManager(self.client, None, None, None)

    def manager_upload(self, file):
        self.transfer.upload(file, self.bucket, file[1:], None, None)

    def upload(self, file, filekey):
        try:
            with open(file, 'rb') as f:
                self.client.upload_fileobj(f, self.bucket, filekey[1:])
        except Exception as ex:
            traceback.print_exc()
            raise
