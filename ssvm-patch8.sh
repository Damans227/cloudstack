# usage: bash -s patch|restore   (runs on the SSVM)
J=/usr/local/cloud/systemvm/cloud-core-4.22.1.1.jar; B=/root/cloud-core-4.22.1.1.jar.orig
E=com/cloud/storage/template/RawImageProcessor.class
if [ "$1" = restore ]; then
  cp -p $B $J && systemctl restart cloud && echo restored && md5sum $J $B; exit
fi
[ -f $B ] || cp -p $J $B
curl -sfo /root/RawImageProcessor.class https://raw.githubusercontent.com/Damans227/cloudstack/lab-files/raw8/RawImageProcessor.class || { echo download failed; exit 1; }
python3 - $B $J /root/RawImageProcessor.class $E <<'PY'
import sys, zipfile, shutil
src, dst, cls, entry = sys.argv[1:]
tmp = dst + '.tmp'
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zo:
    n = 0
    for i in zi.infolist():
        if i.filename == entry:
            zo.writestr(i, open(cls, 'rb').read()); n += 1
        else:
            zo.writestr(i, zi.read(i.filename))
print('replaced', n)
shutil.move(tmp, dst)
PY
unzip -l $J | grep RawImageProcessor
md5sum $J $B
systemctl restart cloud; sleep 5; systemctl is-active cloud
