FROM python:3.5

# docker run --rm --net=host -e EXTRACTOR_QUEUE=ncsa.wordcount -e RABBITMQ_URI="amqp://max:test@host.docker.internal:5672/%2f" maxzilla2/rmq-error-shovel

# environemnt variables
ENV EXTRACTOR_QUEUE="ncsa.image.preview" \
    RABBITMQ_URI="amqp://guest:guest@rabbitmq/%2F" \
    RABBITMQ_MGMT_PORT=15672 \
    RABBITMQ_MGMT_PATH="/" \
    RABBITMQ_MGMT_URL=""

WORKDIR /src

COPY requirements.txt /src/
RUN pip3 install -r /src/requirements.txt

COPY . /src/

CMD python check_rabbitmq.py
