"""Repository-relative paths shared by the generator scripts, so they run from any checkout.

Third-party jars are read from libs/ (not part of the repository - see BUILDING.md) or from the
folder named by the VOYAGER_LIBS environment variable. Previews and scratch files go to tools/out/.
"""
import glob
import os

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
RESOURCES = os.path.join(ROOT, "resources")
LIBS = os.environ.get("VOYAGER_LIBS", os.path.join(ROOT, "libs"))
OUT = os.environ.get("VOYAGER_OUT", os.path.join(TOOLS, "out"))


def res(*parts):
    return os.path.join(RESOURCES, *parts)


def out(*parts):
    os.makedirs(OUT, exist_ok=True)
    return os.path.join(OUT, *parts)


def lib(pattern):
    """The jar in libs/ matching a glob pattern ('minecolonies-*.jar'); raises if it is missing."""
    hits = sorted(glob.glob(os.path.join(LIBS, pattern)))
    if not hits:
        raise FileNotFoundError(f"no {pattern} in {LIBS} - put the jar there or set VOYAGER_LIBS (see BUILDING.md)")
    return hits[0]
