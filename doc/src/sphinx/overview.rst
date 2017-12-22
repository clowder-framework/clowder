.. index:: Overview
Overview
============

What is it good for?
--------------------

* You want to have your web interface or web API close to the data.
* You want to customize how you preview, store, curate the data.
* You have a lot of data but cannot afford cloud storage solutions.

What is it bad for?
-------------------

* If you just want to store small files that don't require a lot of curation, a cloud storage solution might be a better
  fit.

The ugly?
---------

* Documentation is work in progress.
* Some of the code is a little hard to read.

`Contributions <https://github.com/ncsa/clowder/blob/master/CONTRIBUTING.md>`_ in this area are greatly appreciated!
* Documentation is work in progress.
* Some of the code is a little hard to read.

Data Model
------------

Clowder is designed to support any data format and multiple research domains and contains three major extension points:
preprocessing, processing and previewing.

.. container:: imagepadding

    .. image:: _static/data-model.png
        :width: 750px