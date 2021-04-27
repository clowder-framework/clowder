# Clowder documentation build configuration file, created by
# sphinx-quickstart on Wed Mar 26 13:24:40 2014.

# Required for readthedocs. See https://github.com/readthedocs/readthedocs.org/issues/2569
master_doc = 'index'

# -- Path setup --------------------------------------------------------------

# If extensions (or modules to document with autodoc) are in another directory,
# add these directories to sys.path here. If the directory is relative to the
# documentation root, use os.path.abspath to make it absolute, like shown here.
#
# import os
# import sys
# sys.path.insert(0, os.path.abspath('.'))


# -- Project information -----------------------------------------------------

project = 'Clowder'
copyright = '2019, University of Illinois at Urbana-Champaign'
author = 'Luigi Marini'

# The full version, including alpha/beta/rc tags
release = '1.16.0'


# -- General configuration ---------------------------------------------------

# Add any Sphinx extension module names here, as strings. They can be
# extensions coming with Sphinx (named 'sphinx.ext.*') or your custom
# ones.
extensions = [
]

# Add any paths that contain templates here, relative to this directory.
templates_path = ['_templates']

# List of patterns, relative to source directory, that match files and
# directories to ignore when looking for source files.
# This pattern also affects html_static_path and html_extra_path.
exclude_patterns = ['_build', 'Thumbs.db', '.DS_Store']


# -- Options for HTML output -------------------------------------------------

# The theme to use for HTML and HTML Help pages.  See the documentation for
# a list of builtin themes.
#
html_theme = 'sphinx_rtd_theme'

# Add any paths that contain custom static files (such as style sheets) here,
# relative to this directory. They are copied after the builtin static files,
# so a file named "default.css" will overwrite the builtin "default.css".
html_static_path = ['_static']

# The name of an image file (relative to this directory) to place at the top of
# the title page.
latex_logo = '_static/logos_ncsa.png'

# The name of an image file (within the static path) to use as favicon of the
# docs.  This file should be a Windows icon file (.ico) being 16x16 or 32x32
# pixels large.
html_favicon = "../../../public/images/favicon.png"

# Path of logo for menu on right hand side.
html_logo = "../../../public/images/logo_60.png"

# Add any paths that contain custom static files (such as style sheets) here,
# relative to this directory. They are copied after the builtin static files,
# so a file named "default.css" will overwrite the builtin "default.css".
html_static_path = ['_static']

from recommonmark.parser import CommonMarkParser
from recommonmark.transform import AutoStructify

# -- Parser configuration -------------------------------------------------
source_parsers = {
    '.md': CommonMarkParser,
}

source_suffix = ['.rst', '.md']


# -- Parser configuration -------------------------------------------------
def setup(app):
    app.add_stylesheet('css/custom.css')  # custom css
    app.add_config_value('recommonmark_config', {
            # 'url_resolver': lambda url: github_doc_root + url,
            # 'auto_toc_tree_section': 'Contents',
            }, True)
    app.add_transform(AutoStructify)
