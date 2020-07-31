FROM python:3.5

# EXAMPLE USAGE:
# docker run --rm --net=host \
#   -e EXTRACTOR_QUEUE=ncsa.wordcount \
#   -e CLOWDER_HOST=http://host.docker.internal:9000 -e CLOWDER_KEY=r1ek3rs \
#   -e RABBITMQ_URI="amqp://guest:guest@host.docker.internal:5672/%2f" \
# clowder/rmq-error-shovel

# environemnt variables
ENV EXTRACTOR_QUEUE="ncsa.image.preview" \
    CLOWDER_HOST="" \
    CLOWDER_KEY="" \
    RABBITMQ_URI="amqp://guest:guest@rabbitmq/%2F" \
    RABBITMQ_MGMT_PORT=15672 \
    RABBITMQ_MGMT_PATH="/" \
    RABBITMQ_MGMT_URL=""

WORKDIR /src

COPY requirements.txt /src/
RUN pip3 install -r /src/requirements.txt

COPY . /src/

CMD python check_rabbitmq.py
