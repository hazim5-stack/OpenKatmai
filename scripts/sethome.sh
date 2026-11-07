#!/system/bin/sh
OUT=/sdcard/Download/sethome-out.txt
{
echo SETHOME_BEGIN
echo "== home BEFORE =="
cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
echo "== find kiss package =="
PKG=$(pm list packages | grep -i -e kiss -e neamar | head -1 | sed 's/package://' | tr -d '\r')
echo "PKG=$PKG"
if [ -z "$PKG" ]; then
  echo "ERROR: KISS not installed"
else
  echo "== try role add-role-holder =="
  cmd role add-role-holder android.app.role.HOME "$PKG"
  echo "role_exit=$?"
  echo "== try set-home-activity =="
  cmd package set-home-activity "$PKG/fr.neamar.kiss.MainActivity"
  echo "sethome_exit=$?"
fi
echo "== home AFTER =="
cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
echo "== role holders =="
cmd role get-role-holders android.app.role.HOME
echo SETHOME_END
} > "$OUT" 2>&1
