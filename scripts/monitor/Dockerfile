FROM python:3.5

EXPOSE 9999

# environemnt variables
ARG BRANCH="unknown"
ARG VERSION="unknown"
ARG BUILDNUMBER="unknown"
ARG GITSHA1="unknown"
ENV BRANCH=${BRANCH} \
    VERSION=${VERSION} \
    BUILDNUMBER=${BUILDNUMBER} \
    GITSHA1=${GITSHA1} \
    RABBITMQ_URI="amqp://guest:guest@rabbitmq/%2F" \
    RABBITMQ_MGMT_PORT=15672 \
    RABBITMQ_MGMT_PATH="/" \
    RABBITMQ_MGMT_URL=""

RUN pip install pika==0.11.2 requests==2.18.4

WORKDIR /src
COPY monitor.py /src/

CMD python monitor.py
