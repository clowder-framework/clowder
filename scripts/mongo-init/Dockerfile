FROM ubuntu:16.04
MAINTAINER Bing Zhang <bing@illinois.edu>

ENV EMAIL_ADDRESS=""\
    FIRST_NAME=""\
    LAST_NAME="" \
    PASSWORD=""\
    MONGO_URI=""\
    CLOWDER_PORT_9000_TCP_ADDR=""\
    CLOWDER_PORT_9000_TCP_PORT=""\
    RABBITMQ_PORT_15672_TCP_ADDR=""\
    RABBITMQ_PORT_15672_PORT=""\
    MONGO_PORT_27017_TCP_ADDR=""\
    MONGO_PORT_27017_TCP_PORT=""

RUN mkdir -p /data/db && \
    apt-get update && \
    apt-get install -y netcat curl lsb-release && \
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
