Extractors
==============

One of the major features of Clowder is the ability to deploy custom extractors for when files are uploaded to the system.

A full list of extractors is available in `Bitbucket <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS>`_.

To write new extractors, `pyClowder <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/pyclowder/browse>`_ is a good starting point.
It provides a simple Python library to write new extractors in Python. Please see the
`sample extractors <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/pyclowder/browse/sample-extractors>`_ directory for examples.

That being said, extractors can be written in any language that supports HTTP, JSON and AMQP
(ideally a `RabbitMQ client library <https://www.rabbitmq.com/>`_ is available for it).