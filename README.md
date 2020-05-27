# Clowder: Open Source Data Management for Long Tail Data

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.1196568.svg)](https://doi.org/10.5281/zenodo.1196568)

A customizable and scalable data management system you can install in the cloud or on your own hardware.
More information is available at [https://clowderframework.org/](https://clowderframework.org/).

## Running Clowder

There are differet options to run clowder, the easiest and quickest way to get started is by using docker. To
start clowder you will need to have docker and docker-compose installed. Once docker is installed you can start
clowder using docker-compose and the [docker compose file](docker-compose.yml). To launch clowder you will run
`docker-compose up -d` which will download all the containers and start them in the right order. The
docker-compose file will launch all required containers, a [web proxy](https://traefik.io) as well as
three example extractors.

The proxy in the docker-compose file will allow you to access not only clowder, but also will give you access
to ther other aspects of clowder. For example if you run clowder on you local machine you can access the
different aspects of clowder using the following urls:

- [clowder interface](http://localhost:8000/)
- [rabbitmq management](http://localhost:8000/rabbitmq/)
- [traefik interface](http://localhost:8000/traefik/)
- [docker management](http://localhost:8000/portainer/)
- [extractor monitor](http://localhost:8000/monitor/)

The proxy can also generate a SSL certificate using [Let's Encrypt](https://letsencrypt.org/).

### Advanced Installation of Clowder

You will have to manually install dependencies such as [MongoDB](https://www.mongodb.com/),
[RabbitMQ](https://www.rabbitmq.com/) and [ElasticSearch](https://www.elastic.co/). Once the dependencies have
installed you can either run a precompiled version of clowder which can be downloaded from
[NCSA](https://opensource.ncsa.illinois.edu/projects/artifacts.php?key=CATS).  More information on this method
is available on the [wiki](https://opensource.ncsa.illinois.edu/confluence/display/CATS/Installing+Clowder).
You can also run clowder from the source code using `sbt dist`.

## Configuring Clowder

All customizations to a clowder installation can be placed in the custom folder. This makes it easier for
upgrades, since all you need to copy over between different clowder versions is the custom folder (and the
logs folder if you want to keep all logs). The default configuration values are stored in the application.conf
file in the conf folder of clowder. You can override these by placing them in a custom.conf file inside the
custom folder. Plugins that should be enabled can be placed in the play.plugins file in the custom folder.

### Customizing Clowder in Docker

In the case of docker you can override some of the values used by clowder as well as all the other containers
using a .env file that is placed in the same folder as the docker-compose.yml file. Docker-compose when
starting will use this file for environment variables specified in the docker-compose.yml file. Docker-compose
will first use the environment variables set when starting the program, next it will look in the .env file, and
finally it will use any default values specified in the docker-compose.yml file. The [env.example](env.example)
file lists all variables that can be set as well as a short description of what it does. You can for example
use this file to setup Let's Encrypt, or tell clowder to use different security keys instead of the defaults.

## Initializing Clowder

Once clowder has started you will need to create an account. This account can be created using a docker
container. You can start it with `docker run -ti --rm --network clowder-clowder clowder/mongo-init`. The
container will ask for an email address, name, password as well as if this user should be admin (true).
Once the container finishes running, you can login to clowder. 

## Extractors

To run clowder with some example extractors (image, video, pdf and audio) you can start the docker version
of clowder using `docker-compose -f docker-compose.yml -f docker-compose.extractors.yml 
-f docker-compose.override.yml up -d`. This will start the full stack with a few extractors. After a few
minutes the extractors should automatically be registered with clowder.

For a full list of metadata extractors you can deploy to your instance, please take a look at the  
[NCSA repositories](https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS) or 
the [Brown Dog wiki](https://opensource.ncsa.illinois.edu/confluence/display/BD/Project+Supported+Transformations). 
If you have extractors available somewhere else, please get in touch with the team so we can add them these lists.

## Support

For general questions you can write to the mailing list [clowder@lists.illinois.edu](clowder@lists.illinois.edu) or 
join the [Slack workspace](https://join.slack.com/t/clowder-software/shared_invite/enQtMzQzOTg0Nzk3OTUzLTUxYzVhMzZlZDlhMTc0NzNiZTBiNjcyMTEzNjdmMjc5MTA2MTAzMDQwNmUzYTdmNDQyNGMwOWM1Y2YxMzdhNGM).

If you have found a bug, please check that it hasn't been filed already and if not open an issue on 
[GitHub](https://github.com/clowder-framework/clowder/issues) or [Jira](https://opensource.ncsa.illinois.edu/jira/projects/CATS).

## Contributing

For contributing to Clowder see [CONTRIBUTING.md](CONTRIBUTING.md). If you have new ideas and you want to start 
developing please check into Slack to get feedback from other developers. If you want to contribute to the documentation
please follow the same workflow or let the community know of external resources you want to share by advertising on the
mailing list or in Slack.

## License

This software is licensed under the [NCSA Open Source license](https://opensource.org/licenses/NCSA), 
an open source license [based on the MIT/X11 license and the 3-clause BSD license](https://en.wikipedia.org/wiki/University_of_Illinois/NCSA_Open_Source_License).
