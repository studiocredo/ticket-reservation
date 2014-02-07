#!/bin/bash
args=""
if [ "$1" == "debug" ]; then
  args="debug"
fi
play $args -DapplyEvolutions.default=true console
