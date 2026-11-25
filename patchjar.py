import os
import re
import sys
import zipfile

# usage: patchjar.py <original jar> <output jar> <dir with replacement classes>
src, dst, root = sys.argv[1], sys.argv[2], sys.argv[3]

new = {}
for r, _, files in os.walk(root):
    for f in files:
        if f.endswith('.class'):
            p = os.path.join(r, f)
            new[os.path.relpath(p, root)] = open(p, 'rb').read()

# drop every original entry for the replaced classes, including inner classes like Foo$3
groups = set(re.sub(r'\$\d+\.class$', '.class', k) for k in new)


def replaced(name):
    return re.sub(r'\$\d+\.class$', '.class', name) in groups


z = zipfile.ZipFile(src)
out = zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED)
dropped = 0
for i in z.infolist():
    if replaced(i.filename):
        dropped += 1
        continue
    out.writestr(i, z.read(i.filename))
for k in sorted(new):
    out.writestr(k, new[k])
out.close()

check = zipfile.ZipFile(dst)
names = [i.filename for i in check.infolist()]
print('groups', sorted(groups))
print('dropped', dropped, 'added', len(new), 'total', len(names),
      'duplicates', len(names) - len(set(names)), 'test', check.testzip())
