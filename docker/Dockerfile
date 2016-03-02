# Install clowder server
FROM java:8-jre
MAINTAINER Rob Kooper <kooper@illinois.edu>

# expose some properties of the container
EXPOSE 9000
ENV CLOWDER_CONTEXT="/" \
    CLOWDER_ADMINS="" \
    MONGO_URI="" \
    SMTP_HOST="" \
    RABBITMQ_EXCHANGE="clowder" \
    RABBITMQ_VHOST="%2F" \
    RABBITMQ_URI="" \
    RABBITMQ_MGMT_PORT="" \
    ELASTICSEARCH_SERVICE_CLUSTERNAME="clowder" \
    ELASTICSEARCH_SERVICE_SERVER="" \
    ELASTICSEARCH_SERVICE_PORT=""

# copy the build file, this requires sbt dist to be run
COPY files entrypoint.sh /home/clowder/

# create user
RUN useradd -s /bin/bash clowder && \
    chown -R clowder /home/clowder/clowder
USER clowder

# command to run when starting docker
ENTRYPOINT ["/home/clowder/entrypoint.sh"]
CMD ["server"]
