#!/bin/bash
# runs the stock and the fixed createtmplt.sh against a device that fails every read
STOCK=/usr/share/cloudstack-common/scripts/storage/qcow2/createtmplt.sh
FIXED=/tmp/fix6/fix6-createtmplt.sh
DEV=fix6err

hostname; date -u
dmsetup create $DEV --table "0 2097152 error" || exit 1

echo "== stock script"
bash $STOCK -t tmp/fix6/stock -n t.qcow2 -f /dev/mapper/$DEV > /tmp/fix6/stock.out 2>&1
echo "exit=$?"
grep -m2 "error while reading" /tmp/fix6/stock.out
qemu-img info /tmp/fix6/stock/t.qcow2 2>/dev/null | grep -E "virtual size|disk size"
ls /tmp/fix6/stock/template.properties 2>/dev/null

echo "== fixed script"
bash $FIXED -t tmp/fix6/fixed -n t.qcow2 -f /dev/mapper/$DEV > /tmp/fix6/fixed.out 2>&1
echo "exit=$?"
tail -c 200 /tmp/fix6/fixed.out; echo
ls /tmp/fix6/fixed/template.properties 2>/dev/null || echo "no template.properties written"

dmsetup remove $DEV
rm -rf /tmp/fix6
echo "cleaned up: $(dmsetup ls | grep -c $DEV) devices left"
