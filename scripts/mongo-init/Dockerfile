FROM python:3.7
MAINTAINER Bing Zhang <bing@illinois.edu>

ENV USERNAME="" \
    EMAIL_ADDRESS="" \
    FIRST_NAME="" \
    LAST_NAME="" \
    PASSWORD="" \
    ADMIN="" \
    MONGO_URI="mongodb://mongo:27017/clowder"

COPY requirements.txt /
RUN pip install -r /requirements.txt

# Copy files
COPY mongo-init.py /
CMD python /mongo-init.py
