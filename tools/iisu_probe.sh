# Read-only probe of iiSU's private settings, run by Settings > Thor settings > Run script as Root.
# AYN sends this file to its root service ONE LINE AT A TIME: no variables, no multi-line blocks.
mkdir -p /data/local/tmp/iisu_probe
id > /data/local/tmp/iisu_probe/id.txt 2>&1
ls -laR /data/data/com.iisulauncher > /data/local/tmp/iisu_probe/listing.txt 2>&1
chmod -R a+rX /data/local/tmp/iisu_probe
# Pass 2: the appearance settings only (Jetpack DataStore), one timestamped copy per run. The *_secure.xml files are never copied.
cp /data/data/com.iisulauncher/files/datastore/launcher_settings.preferences_pb /data/local/tmp/iisu_probe/settings_$(date +%s).pb
chmod -R a+rX /data/local/tmp/iisu_probe
