.. index:: Application Programming Interface
Using the API
=============

The RESTful application programming interface is the best way to interact with Clowder programmatically. Much of the web
frontend and the extractors use this same API to interact with the system. For a full list of available endpoints please
see the Swagger documentation:

https://clowder.ncsa.illinois.edu/swagger/?url=https://clowder.ncsa.illinois.edu/clowder/swagger

For more information about the endpoints take a look at the source code in ``app/api`` package. All routes are defined
in ``conf/routes``. For methods that write to the system a key is required. The default key is available in
``conf/application.conf`` under ``commKey``. Please change this to a very long string of characters when deploying the
system. The key is passed as a query parameter to the url. For example ``?key=sdjof902j39f09joahsduh0932jnujv09erjfosind``.

You can use ``curl`` to test the service. If you are on Linux or MacOSX you should have it already. Try typing ``curl``
on the command prompt. If you are on windows, you can download a build at http://curl.haxx.se/.
If you prefer more of a GUI experience, you can try `Postman <https://www.getpostman.com/>`_.

pyClowder2 API wrapper in Python
--------------------------------

If you are writing Python scripts or extractors against the Clowder API, the `pyClowder2 library <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/pyclowder2/browse>`_ provides some of the
API functionality with simplified wrapper functions. After using ``python setup.py install`` to install the library,
you can use it to get and post data to Clowder.

**When to use**

pyClowder2 provides straight forward submodules for various Clowder API endpoints. Python scripts that interact with
Clowder can usually be simplified by replacing custom implementations with calls to the appropriate pyClowder2 methods.

- **files** (e.g. upload/download/get metadata/update/submit for extraction)
- **datasets** (e.g. create/download/get metadata & contents/submit for extraction)
- **collections** (e.g. create/get datasets)
- Additional functionality (such as support for geostreams) in development.

For details about pyClowder2 functions and how they can be used, please see the `library documentation <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/pyclowder2/browse/docs>`_.

pyClowder2 is updated as relevant API endpoints in Clowder are added or changed, so by using this library your code is better insulated from breaking changes as well.