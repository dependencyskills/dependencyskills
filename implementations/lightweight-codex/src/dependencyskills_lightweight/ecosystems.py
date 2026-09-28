"""The source-shipping ecosystems the lookup reads without a build plugin, in the order it looks for them."""

from pathlib import Path

from . import cargo, golang, npm, pypi

MODULES = (npm, pypi, golang, cargo)


def project_kind(directory):
    """The ecosystem module whose project is rooted at `directory`, or None."""
    for module in MODULES:
        if module.is_project(Path(directory)):
            return module
    return None
