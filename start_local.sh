#!/bin/bash
cmd=$1
if [ -z "$cmd" ]; then
  cmd=run
fi
shift

dir=$(dirname "$0")
conf_dir="$dir/conf.local"
pkFile=$(find "$conf_dir" -name '*.der' | head -1)
conf="$conf_dir/application.local.conf"

_JAVA_OPTIONS="-Dakka.log-dead-letters=true -Dakka.log-dead-letters-during-shutdown=true -Dconfig.file=$conf -Daws.key-pair.private-key.path=$pkFile -Djava.library.path=$dir/../jnotify" sbt -jvm-debug 9999 play "$cmd"
