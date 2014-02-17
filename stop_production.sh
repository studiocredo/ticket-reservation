#!/bin/bash
if [ -f RUNNING_PID ]; then
  echo "[info] Stopping application (with PID `cat RUNNING_PID`)..."
  kill `cat RUNNING_PID`

  RESULT=$?

  if test "$RESULT" = 0; then
    echo "[info] Done!"
    exit 0
  else
    echo "[\033[31merror\033[0m] Failed ($RESULT)"
    exit $RESULT
  fi
fi
