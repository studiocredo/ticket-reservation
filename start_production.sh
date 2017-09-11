#!/bin/bash
cmd=$1
if [ -z "$cmd" ]; then
  cmd=start
fi
shift

dir=$(dirname "$0")
conf_dir="$dir/scripts/private"
pkFile=$(find "$conf_dir" -name '*.der' | head -1)
conf="$conf_dir/application.production.conf"
log_resource="production-logger.xml"

_JAVA_OPTIONS="-Dconfig.file=$conf -Daws.key-pair.private-key.path=$pkFile -Dlogger.resource=$log_resource -Dhttp.port=9000 -DapplyEvolutions.default=true"
if [ "$cmd" == "start" ]; then
  _JAVA_OPTIONS="$_JAVA_OPTIONS -Xms16G -Xmx16G"
fi
export _JAVA_OPTIONS
sbt play "$cmd"
