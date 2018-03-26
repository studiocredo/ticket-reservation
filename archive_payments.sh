#!/bin/bash
set -e

date=$1
amount=$2

die() {
  local message="$1"
  local code="${2:-1}"
  if [[ -n "$message" ]] ; then
    echo "ERROR: ${message}"
  else
    echo "ERROR: Execution failed"
  fi
  exit "${code}"
}

usage() {
    echo "$(basename "$0") <date> [amount]"
    echo "  date  : date, only archive payments before that date"
    echo "  amount: optional amount to filter on"
}

if [ -z "$date" ]; then
  usage
  die 'Provide at least date'
fi

dateArg=$(gdate -d "$date" -I)

if [ -n "$amount" ]; then
cat <<EOF
update "payment" set order_id = null, archived = true where amount = $amount and archived is false and date < date '$dateArg';
EOF
else
cat <<EOF
update "payment" set order_id = null, archived = true where date < date '$dateArg' and archived is false;
EOF
fi


