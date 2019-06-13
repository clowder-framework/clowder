# ----------------------------------------------------------------------
# BUILD CLOWDER DIST
# ----------------------------------------------------------------------
FROM java:jdk-alpine as clowder-build

WORKDIR /src

# install clowder libraries (hopefully cached)
COPY sbt* /src/
COPY project /src/project
RUN ./sbt update

# compile clowder
COPY lib /src/lib/
COPY conf /src/conf/
COPY public /src/public/
COPY app /src/app/
RUN ./sbt dist \
    && unzip -q target/universal/clowder-*.zip \
    && mv clowder-* clowder \
    && mkdir -p clowder/custom clowder/logs

# ----------------------------------------------------------------------
# BUILD CLOWDER
# ----------------------------------------------------------------------
FROM java:jre-alpine

# add bash
RUN apk add --no-cache bash curl

# expose some properties of the container
EXPOSE 9000

# working directory
WORKDIR /home/clowder

# customization including data
VOLUME /home/clowder/custom /home/clowder/data

# copy the build file, this requires sbt dist to be run (will be owned by root)
COPY --from=clowder-build /src/clowder /home/clowder/
COPY docker/clowder.sh docker/healthcheck.sh /home/clowder/
COPY docker/custom.conf docker/play.plugins /home/clowder/custom/

# command to run when starting docker
CMD /home/clowder/clowder.sh

# health check
HEALTHCHECK CMD /home/clowder/healthcheck.sh
