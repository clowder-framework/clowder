FROM python:3.7

LABEL maintainer="Bing Zhang <bing@illinois.edu>"

ENV SERVICE_ENDPOINT='http://localhost:8000' \
    BUCKET='localbucket' \
    AWS_ACCESS_KEY_ID="" \
    AWS_SECRET_ACCESS_KEY="" \
    REGION="us-east-1"\
    DBURL="mongodb://localhost:27017" \
    DBNAME="clowder" \
    OUTPUTFOLDER="/output"


COPY requirements.txt /
RUN pip install -r /requirements.txt

# Copy files
COPY main.py s3.py /
CMD python /main.py --dburl $DBURL --dbname $DBNAME
