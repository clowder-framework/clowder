FROM python:3.7-alpine

# environemnt variables
ARG BRANCH="unknown"
ARG VERSION="unknown"
ARG BUILDNUMBER="unknown"
ARG GITSHA1="unknown"
ENV BRANCH=${BRANCH} \
    VERSION=${VERSION} \
    BUILDNUMBER=${BUILDNUMBER} \
    GITSHA1=${GITSHA1} \
    RABBITMQ_URI=amqp://guest:guest@rabbitmq/%2F \
    MONGO_URI=mongodb://mongodb:27017/

COPY requirements.txt /src/
RUN pip install -r /src/requirements.txt

COPY check_* /src/

USER 1000
WORKDIR /src
CMD ./check_rabbitmq
