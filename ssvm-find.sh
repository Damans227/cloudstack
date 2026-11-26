hostname
systemctl cat cloud 2>/dev/null | grep -E 'Exec|Working'
P=$(pgrep -f java | head -1); echo "java pid $P cwd $(readlink /proc/$P/cwd)"
find / -xdev -name '*.jar' 2>/dev/null | grep -v /usr/lib/jvm | while read j; do unzip -l "$j" 2>/dev/null | grep -q 'com/cloud/storage/template/RawImageProcessor.class' && echo "FOUND $j"; done
