"""Write render JSON (zlib+base64) for every design into tools/out/ and zip them for the Blender renderer."""
import os, sys, zlib, base64, zipfile
from designs import launchpad, endgate
import paths
out = paths.out()
names = sys.argv[1:] or [f'{k}{l}' for l in range(1, 6) for k in ('launchpad', 'endgate')]
for name in names:
    fn = launchpad if name.startswith('launchpad') else endgate
    s = fn(int(name[-1]))
    open(f'{out}/{name}.b64', 'w').write(base64.b64encode(zlib.compress(s.to_render_json().encode(), 9)).decode())
zpath = paths.out('voyager_structs.zip')
with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
    for name in names:
        z.write(f'{out}/{name}.b64', f'{name}.b64')
    z.write(os.path.join(paths.TOOLS, 'mcrender.py'), 'mcrender.py')
print(zpath, os.path.getsize(zpath), names)
