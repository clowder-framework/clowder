FROM ubuntu:16.04
MAINTAINER Bing Zhang <bing@illinois.edu>

ENV EMAIL_ADDRESS=""\
    FIRST_NAME=""\
    LAST_NAME="" \
    PASSWORD=""\
    MONGO_URI=""

RUN mkdir -p /data/db && \
    apt-get update && \
    apt-get install -y lsb-release && \
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv EA312927 && \
    echo "deb http://repo.mongodb.org/apt/ubuntu "$(lsb_release -sc)"/mongodb-org/3.2 multiverse" | tee /etc/apt/sources.list.d/mongodb-org-3.2.list && \
    apt-get update && apt-get install -y \
    mongodb-org \
    net-tools iproute \
    python3 python3-pip && \
    pip3 install passlib bcrypt

#Copy files
COPY FILES.mongo-init /

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
