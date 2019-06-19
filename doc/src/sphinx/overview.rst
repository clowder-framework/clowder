.. index:: Overview

********
Overview
********

Clowder has been build from the ground up to be easily customizable for different research and applications domains. Here
some of the reasons why you would want to adopt it:

* You want both an extensive web interface and web service API to be able to easily browse the data in the web browser
  and also script how you manipulate the data.
* You want to customize how you preview, store and curate the data.
* You have code you want to run on a subset of the data as it is ingested into the system.
* You have a lot of data and prefer hosting the data yourself instead of paying for cloud storage solutions.

There is no single Clowder instance. Clowder is open source software that can be installed and maintained by individual
users, research labs, data centers.

Data Model
----------

The basic data model is very generic to support many different cases. This means that specific communities will have to
adopt and customize how the different resource types are used within a specific use case.

.. container:: imagepadding

    .. image:: _static/data-model.png
        :width: 80%
        :align: center

