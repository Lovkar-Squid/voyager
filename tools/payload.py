import sys, zlib, base64, json
from designs import launchpad, endgate
import paths
name = sys.argv[1]
fn = launchpad if name.startswith('launchpad') else endgate
s = fn(int(name[-1]))
payload = base64.b64encode(zlib.compress(s.to_render_json().encode(), 9)).decode()
open(paths.out(f'payload_{name}.txt'), 'w').write(payload)
print(name, len(payload))
