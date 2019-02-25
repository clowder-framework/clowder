FROM ubuntu:16.04
MAINTAINER Bing Zhang <bing@illinois.edu>

ENV USERNAME="" \
    EMAIL_ADDRESS="user@example.com" \
    FIRST_NAME="Example" \
    LAST_NAME="User" \
    PASSWORD="" \
    ADMIN="false" \
    MONGO_URI="mongodb://mongo:27017/clowder"

RUN apt-get update \
    && apt-get install -y \
        python-pymongo \
        python-passlib \
        python-bcrypt \
    && rm -rf /var/lib/apt/lists/*

#Copy files
WORKDIR /
COPY mongo-init.py /
CMD python mongo-init.py
