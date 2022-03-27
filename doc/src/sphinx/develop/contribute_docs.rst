.. index:: How to Contribute Documentation

How to Contribute Documentation
=====================================

Documentation is stored in ``doc/src/sphinx``. 

Dependencies are stored in ``doc/src/sphinx/requirements.txt``.




.. tab-set::

  .. tab-item:: conda

    Create a virtual environment for documentation: 

    .. code:: bash

      conda create -n clowder_docs python=3.7 -y
      conda activate clowder_docs

    Install doc dependencies. It's always better to run all conda commands before installing pip packages.

    .. code:: bash

      conda install sphinx==3.1.2 recommonmark==0.6.0 jinja2==3.0.1 m2r2==0.3.2 -y
      pip install sphinx-rtd-theme==0.5.0 sphinx_design==0.0.13 docutils==0.16 certifi==2021.5.30 sphinx-autobuild==2021.3.14

  .. tab-item:: pyenv

    Create a virtual environment for documentation:

    .. code:: bash

      pyenv install 3.7
      pyenv virtualenv 3.7 clowder_docs

      # make virtual environemnt auto-activate
      cd doc/src/sphinx
      pyenv local clowder_docs

    Install doc dependencies: 

    .. code:: bash

      pip install sphinx==3.1.2 recommonmark==0.6.0 jinja2==3.0.1 m2r2==0.3.2 sphinx-rtd-theme==0.5.0 sphinx_design==0.0.13 docutils==0.16 certifi==2021.5.30

Now, build HTML docs for viewing: 

    .. code:: bash

      cd doc/src/sphinx
      sphinx-autobuild . _build/html

Open http://127.0.0.1:8000 in your browser. Saved change will be auto-updated in the browser.


.. dropdown:: (Optional alternative) Static builds

    If you do not want dynamic builds, you can statically generate the HTML this way.

    .. code:: bash

      cd doc/src/sphinx
      make html
    
    View docs by opening ``index.html`` in the browser
    ``clowder/doc/src/sphinx/_build/html/index.html``



⭐ If you experience *any* trouble, come ask us on `Slack here <https://join.slack.com/t/clowder-software/shared_invite/enQtMzQzOTg0Nzk3OTUzLTYwZDlkZDI0NGI4YmI0ZjE5MTZiYmZhZTIyNWE1YzM0NWMwMzIxODNhZTA1Y2E3MTQzOTg1YThiNzkwOWQwYWE>`_! ⭐

.. note::
  
  To see how to install Clowder, please see :ref:`installing_clowder`.
