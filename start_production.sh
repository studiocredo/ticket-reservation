#!/bin/bash
cmd=$1
if [ -z "$cmd" ]; then
  cmd=start
fi

dir=$(dirname "$0")
prod_conf="$dir/scripts/private/production.conf"
log_conf="$dir/scripts/private/production-logger.xml"
_JAVA_OPTIONS="-Dconfig.file=$prod_conf -Dlogger.file=$log_conf -Dhttp.port=9000"
if [ "$cmd" == "start" ]; then
  _JAVA_OPTIONS="$_JAVA_OPTIONS -Xms4G -Xmx4G"
fi
export _JAVA_OPTIONS
sbt play "$cmd"
