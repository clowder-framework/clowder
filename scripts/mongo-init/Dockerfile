FROM python:3.7
MAINTAINER Bing Zhang <bing@illinois.edu>

# environemnt variables
ARG BRANCH="unknown"
ARG VERSION="unknown"
ARG BUILDNUMBER="unknown"
ARG GITSHA1="unknown"
ENV BRANCH=${BRANCH} \
    VERSION=${VERSION} \
    BUILDNUMBER=${BUILDNUMBER} \
    GITSHA1=${GITSHA1} \
    USERNAME="" \
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
