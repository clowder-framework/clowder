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
    ELASTICSEARCH_SERVICE_PORT="" \
    TOOLMANAGER_URI=""

# create the user
RUN useradd -s /bin/bash clowder

# copy the build file, this requires sbt dist to be run (will be owned by root)
COPY files entrypoint.sh /home/clowder/

# create some folders owned by clowder user
RUN mkdir -p /home/clowder/clowder/custom /home/clowder/clowder/logs \
    && chown clowder /home/clowder/clowder /home/clowder/clowder/custom /home/clowder/clowder/logs

# command to run when starting docker
ENTRYPOINT ["/home/clowder/entrypoint.sh"]
CMD ["server"]

# change to user clowder
USER clowder
