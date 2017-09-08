#!/bin/bash
cmd=$1
if [ -z "$cmd" ]; then
  cmd=run
fi

dir=$(dirname "$0")
conf_dir="$dir/conf.local"
pkFile=$(find "$conf_dir" -name '*.der' | head -1)
conf="$conf_dir/application.local.conf"

_JAVA_OPTIONS="-Dconfig.file=$conf -Daws.key-pair.private-key.path=$pkFile -Djava.library.path=$dir/../jnotify" sbt play "$cmd"
