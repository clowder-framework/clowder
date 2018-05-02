# Clowder: Open Source Data Management for Long Tail Data

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.1196568.svg)](https://doi.org/10.5281/zenodo.1196568)

A customizable and scalable data management system you can install in the cloud or on your own hardware.
More information is available at [https://clowder.ncsa.illinois.edu/](https://clowder.ncsa.illinois.edu/).

## Installation

There are three options to install and run Clowder:

- Releases are available [here](https://opensource.ncsa.illinois.edu/projects/artifacts.php?key=CATS). You will have to 
  manually install dependencies such as [MongoDB](https://www.mongodb.com/), [RabbitMQ](https://www.rabbitmq.com/) and [ElasticSearch](https://www.elastic.co/).
  More information on this method is available on the [wiki](https://opensource.ncsa.illinois.edu/confluence/display/CATS/Installing+Clowder).
- The [docker compose file](docker-compose.yml) allows you to run the full stack using [Docker](https://www.docker.com/).
  You can learn more about Docker compose [here](https://docs.docker.com/compose/). More information about this method
  is available on the [wiki](https://opensource.ncsa.illinois.edu/confluence/display/CATS/Running+Clowder+using+Docker).
- Build it from scratch. Check out the code and run `sbt dist`. Clowder core is a [Play](https://www.playframework.com/) application.

For a full list of metadata extractors you can deploy to your instance, please take a look at the  
[NCSA repositories](https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS) or 
the [Brown Dog wiki](https://opensource.ncsa.illinois.edu/confluence/display/BD/Project+Supported+Transformations). 
If you have extractors available somewhere else, please get in touch with the team so we can add them these lists.

## Support

For general questions you can write to the mailing list [clowder@lists.illinois.edu](clowder@lists.illinois.edu) or 
join the [Slack workspace](https://join.slack.com/t/clowder-software/shared_invite/enQtMzQzOTg0Nzk3OTUzLTUxYzVhMzZlZDlhMTc0NzNiZTBiNjcyMTEzNjdmMjc5MTA2MTAzMDQwNmUzYTdmNDQyNGMwOWM1Y2YxMzdhNGM).

If you have found a bug, please check that it hasn't been filed already and if not open an issue on 
[GitHub](https://github.com/ncsa/clowder/issues) or [Jira](https://opensource.ncsa.illinois.edu/jira/projects/CATS).


## Contributing

For contributing to Clowder see [CONTRIBUTING.md](CONTRIBUTING.md). If you have new ideas and you want to start 
developing please check into Slack to get feedback from other developers. If you want to contribute to the documentation
please follow the same workflow or let the community know of external resources you want to share by advertising on the
mailing list or in Slack.

## License

This software is licensed under the [NCSA Open Source license](https://opensource.org/licenses/NCSA), 
an open source license [based on the MIT/X11 license and the 3-clause BSD license](https://en.wikipedia.org/wiki/University_of_Illinois/NCSA_Open_Source_License).
