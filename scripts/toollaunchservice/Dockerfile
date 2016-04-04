FROM ubuntu
RUN apt-get -y update \
    && apt-get -y install curl unzip docker.io python python-dev python-pip \
    && pip install flask-restful \
    && pip install arrow \
    && apt-get clean all \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* \
    ;
COPY FILES.toolserver /
ENV TOOLSERVER_PORT 8082
CMD /usr/local/bin/usage

