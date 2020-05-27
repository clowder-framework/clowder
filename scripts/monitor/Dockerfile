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

WORKDIR /src

COPY requirements.txt /src/
RUN pip3 install -r /src/requirements.txt

COPY . /src/

CMD python monitor.py
