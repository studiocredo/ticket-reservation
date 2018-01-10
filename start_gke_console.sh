#!/bin/bash
dir=$(dirname "$0")
conf_dir="$dir/conf.local"
pkFile=$(find "$conf_dir" -name '*.der' | head -1)
conf="$conf_dir/application.local.conf"
pwdFile="$(apparix -lm infrastructure | cut -d , -f 3)/base/prd/password.txt"

_JAVA_OPTIONS="-Dconfig.file=$conf -Daws.key-pair.private-key.path=$pkFile -Dpostgres.user=studiocredo -Dpostgres.password=$(cat $pwdFile) -Dpostgres.db=ticket_reservation"
export _JAVA_OPTIONS
sbt play console
