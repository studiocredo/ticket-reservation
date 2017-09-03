confDir=$(dirname "$0")/conf.local
pkFile=$(find "$confDir" -name '*.der' | head -1)
sbt "start -Dconfig.file=$confDir/application.local.conf -Daws.key-pair.private-key.path=$pkFile"
