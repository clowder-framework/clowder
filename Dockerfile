# ----------------------------------------------------------------------
# BUILD CLOWDER DIST
# ----------------------------------------------------------------------
FROM openjdk:8-jdk-bullseye as clowder-build

ARG BRANCH="unknown"
ARG VERSION="unknown"
ARG BUILDNUMBER="unknown"
ARG GITSHA1="unknown"

WORKDIR /src

# install clowder libraries (hopefully cached)
COPY sbt* /src/
COPY project /src/project
RUN ./sbt update

# environemnt variables
ENV BRANCH=${BRANCH} \
    VERSION=${VERSION} \
    BUILDNUMBER=${BUILDNUMBER} \
    GITSHA1=${GITSHA1}

# compile clowder
COPY lib /src/lib/
COPY conf /src/conf/
COPY public /src/public/
COPY app /src/app/
RUN rm -rf target/universal/clowder-*.zip clowder clowder-* \
    && ./sbt dist \
    && unzip -q target/universal/clowder-*.zip \
    && mv clowder-* clowder \
    && apt-get update && apt-get -y install zip \
    && for x in $(find clowder -name \*.jar); do \
         zip -d $x org/apache/log4j/net/JMSAppender.class org/apache/log4j/net/SocketServer.class | grep 'deleting:' && echo "fixed $x"; \
       done; \
       echo "removed JMSAppender and SocketServer" \
    && mkdir -p clowder/custom clowder/logs

# ----------------------------------------------------------------------
# BUILD CLOWDER
# ----------------------------------------------------------------------
FROM openjdk:8-jre-bullseye as clowder-runtime

# environemnt variables
ARG BRANCH="unknown"
ARG VERSION="unknown"
ARG BUILDNUMBER="unknown"
ARG GITSHA1="unknown"
ENV BRANCH=${BRANCH} \
    VERSION=${VERSION} \
    BUILDNUMBER=${BUILDNUMBER} \
    GITSHA1=${GITSHA1}

# expose some properties of the container
EXPOSE 9000

# working directory
WORKDIR /home/clowder

# customization including data
VOLUME /home/clowder/custom /home/clowder/data

# copy the build file, this requires sbt dist to be run (will be owned by root)
COPY --chown=0:0 --from=clowder-build /src/clowder /home/clowder/
COPY docker/clowder.sh docker/healthcheck.sh /home/clowder/
COPY docker/custom.conf docker/play.plugins /home/clowder/custom/

# Containers should NOT run as root as a good practice
# numeric id to be compatible with openshift, will run as random userid:0
RUN mkdir -p /home/clowder/data && \
    chmod 777 /home/clowder/logs /home/clowder/data /home/clowder/custom
USER 10001

# command to run when starting docker
CMD /home/clowder/clowder.sh

# health check
HEALTHCHECK CMD /home/clowder/healthcheck.sh
