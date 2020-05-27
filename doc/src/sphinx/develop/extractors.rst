.. index:: Extractors

Extractors
==============

One of the major features of Clowder is the ability to deploy custom extractors for when files are uploaded to the system.
A full list of extractors is available in `Bitbucket <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS>`_.

To write new extractors, `pyClowder2 <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/pyclowder2/browse>`_ is a good starting point.
It provides a simple Python library to write new extractors in Python. Please see the
`sample extractors <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/pyclowder2/browse/sample-extractors>`_ directory for examples.
That being said, extractors can be written in any language that supports HTTP, JSON and AMQP
(ideally a `RabbitMQ client library <https://www.rabbitmq.com/>`_ is available for it).

Clowder capture several events and sends out specific messages for each. Extractors that are registered for a specific
event type will receive the message and can then act on it. This is defined in the extractor manifest.

The current list of supported events is:

* File uploaded
* File added to dataset
* File Batch uploaded to dataset
* File remove from dataset
* Metadata added to file
* Metadata remove from file
* Metadata added to dataset
* Metadata removed from dataset
* File/Dataset manual submission to extractor


