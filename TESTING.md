# Testing Clowder PR

Download the [docker-compose.yml](https://raw.githubusercontent.com/clowder-framework/clowder/develop/docker-compose.yml) file in a new folder. Next create a .env file with the following data:

```ini
COMPOSE_PROJECT_NAME=clowder
TRAEFIK_HOST=Host:yourmachine.ncsa.illinois.edu;
TRAEFIK_HTTP_PORT=80
TRAEFIK_HTTPS_PORT=443
TRAEFIK_HTTPS_OPTIONS=TLS
TRAEFIK_ACME_ENABLE=true
TRAEFIK_ACME_EMAIL=youremail@ncsa.illinois.edu
TRAEFIK_HTTP_REDIRECT=Redirect.EntryPoint:https
CLOWDER_SSL=true
CLOWDER_ADMINS=youremail@ncsa.illinois.edu
```

Next create a docker-compose.override.yml file:

```yaml
version: '3.5'

services:
  # point to the PR image (in this case PR-404)
  clowder:
    image: ghcr.io/clowder-framework/clowder:PR-404

  # add any more extractors if you want
  # extract preview image
  imagepreview:
    image: clowder/extractors-image-preview:latest
    restart: unless-stopped
    networks:
      - clowder
    depends_on:
      rabbitmq:
        condition: service_started
    environment:
      - RABBITMQ_URI=${RABBITMQ_URI:-amqp://guest:guest@rabbitmq/%2F}

  # extract image metadata
  imagemetadata:
    image: clowder/extractors-image-metadata:latest
    restart: unless-stopped
    networks:
      - clowder
    depends_on:
      rabbitmq:
        condition: service_started
    environment:
      - RABBITMQ_URI=${RABBITMQ_URI:-amqp://guest:guest@rabbitmq/%2F}

  # digest
  digest:
    image: clowder/extractors-digest:latest
    restart: unless-stopped
    networks:
      - clowder
    depends_on:
      rabbitmq:
        condition: service_started
    environment:
      - RABBITMQ_URI=${RABBITMQ_URI:-amqp://guest:guest@rabbitmq/%2F}
```

It is best practice to start with a `docker-compose pull` to make sure you have all the latest versions of the containers, followed by a  `docker-compose up -d`. This will start all containers. You should be able to go to https://yourmachine.ncsa.illinois.edu.

If this is the first time running the stack (or if you removed the mongo database), you will need to create the initial user again:

```bash
docker run --rm -it \
    --network clowder_clowder \
    -e "FIRSTNAME=Admin" \
    -e "LASTNAME=User" \
    -e "ADMIN=true" \
    -e "PASSWORD=areallygoodpassword" \
    -e "EMAIL_ADDRESS=youremail@ncsa.illinois.edu" \
    -e "MONGO_URI=mongodb://mongo:27017/clowder" \
    clowder/mongo-init
```


