FROM ubuntu:16.04
MAINTAINER Bing Zhang <bing@illinois.edu>
USER root

ENV EMAIL_ADDRESS=""\
    PASSWORD=""\
    HOSTIP=""\
    FIRST_NAME=""\
    LAST_NAME=""

RUN useradd -s /bin/bash user

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
COPY files/entrypoint.sh files/passwd /home/user/

#Change owner and group
RUN chown user /home/user && chown user /home/user/passwd && chown user /home/user/entrypoint.sh && chmod u+x /home/user/passwd && chmod u+x /home/user/entrypoint.sh
#Set workdir
WORKDIR "/home/user"

ENTRYPOINT ["/home/user/entrypoint.sh"]
