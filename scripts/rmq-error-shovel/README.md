Error queue cleanup up script
=============================

Script to remove files from an error queue if a message refers to a file that doesn't exist anymore. Otherwise moves the
message to the appropriate queue for reprocessing.

This should be executed when needed and not on a timer in case there are other types of error that would result in an
endless loop of messages going from execution queue to error queue and back to execution queue.

To build the container run `docker build -t clowder/rmq-error-shovel .`

To run the container on the a specific queue, for example `ncsa.image.preview` run this command (use your api key and 
change other parameters as needed):

```
docker run -t --rm --net=host -e EXTRACTOR_QUEUE=ncsa.image.preview -e CLOWDER_HOST=http://host.docker.internal:9000 
-e CLOWDER_KEY=your_api_key -e RABBITMQ_URI="amqp://guest:guest@host.docker.internal:5672/%2f" 
clowder/rmq-error-shovel
``` 
