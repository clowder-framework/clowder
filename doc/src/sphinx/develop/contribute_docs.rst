.. index:: How to Contribute Documentation

How to Contribute Documentation
=====================================

Documentation is stored in ``doc/src/sphinx``. 

Dependencies are stored in ``doc/src/sphinx/requirements.txt``.




.. tab-set::

  .. tab-item:: conda

    Create a virtual environment for documentation: 

    .. code:: bash

      conda create -n clowder_docs python=3.8 -y
      conda activate clowder_docs

    Now we must edit the `requirements.txt` file to be compatible with Conda. These packages are not available on conda-forge. 
    
    Comment out the top three lines like so:

    .. code:: properties

      # -i https://pypi.org/simple/
      # sphinx-rtd-theme==0.5.0
      # sphinx_design==0.0.13
      ... 

    Install the dependencies. It's always better to run all conda commands before installing pip packages.

    .. code:: bash

      conda install --file requirements.txt -y
      pip install sphinx-rtd-theme==0.5.0 sphinx_design==0.0.13

  .. tab-item:: pyenv

    Create a virtual environment for documentation:

    .. code:: bash

      pyenv install 3.7.12 # or any 3.{7,8,9}
      pyenv virtualenv 3.7.12 clowder_docs

      # make virtual environemnt auto-activate
      cd doc/src/sphinx
      pyenv local clowder_docs

    Install doc dependencies: 

    .. code:: bash

      pip install -r requirements.txt

Now, build HTML docs for viewing: 

    .. code:: bash

      # run from doc/src/sphinx
      sphinx-autobuild . _build/html

Open http://127.0.0.1:8000 in your browser. Saved changes will be auto-updated in the browser.


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
