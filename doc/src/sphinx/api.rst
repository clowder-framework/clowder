.. index:: Application Programming Interface

Web Service API
=============

The RESTful application programming interface is the best way to interact with Clowder programmatically. Much of the web
frontend and the extractors use this same API to interact with the system. For a full list of available endpoints please
see the `Swagger documentation <https://clowderframework.org/swagger/?url=https://clowderframework.org/clowder/swagger>`_.

Depending on the privacy settings of a specific Clowder instance, a user API key might be required. Users can create
API keys through the web UI on their profile page (upper right user icon on any page). API Keys can be provided in the
HTTP request as URL parameters, for example ``?key=*yourapikey*`` or using the HTTP header ``X-API-Key: *yourapikey*``. The HTTP
header method is preferred (more secure) but some environments / libraries might make it easer to provide the API key
as a URL parameter.

You can use ``curl`` to test the service. If you are on Linux or MacOSX you should have it already. Try typing ``curl``
on the command prompt. If you are on windows, you can download a build at http://curl.haxx.se/.
If you prefer more of a GUI experience, you can try `Postman <https://www.getpostman.com/>`_.

For example, the following examples request the metadata attached to a dataset.

Here is an example of requesting the metadata attached to a *dataset* and providing the API key as a URL parameter:

.. code-block:: bash

  curl -X GET "https://clowderframework.org/clowder/api/datasets/5cd47b055e0e57385688f788/metadata.jsonld?key=*yourapikey*"

Here is an example of requesting the metadata attached to a *file* and providing the API key as the HTTP header *X-API-Key*:

.. code-block:: bash

  curl -X GET -H "X-API-Key: *yourapikey*" "https://clowderframework.org/clowder/api/files/5d07b5fe5e0ec351d75ff064/metadata.jsonld"
