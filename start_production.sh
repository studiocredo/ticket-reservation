#!/bin/bash
cmd=$1
if [ -z "$cmd" ]; then
  cmd=start
fi

dir=$(dirname "$0")
prod_conf="$dir/scripts/private/production.conf"
log_conf="$dir/scripts/private/production-logger.xml"
opts="$cmd -Dconfig.file=$prod_conf -Dlogger.file=$log_conf -Dhttp.port=9000"
sbt play "$opts"
