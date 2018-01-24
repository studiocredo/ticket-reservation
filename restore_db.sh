#!/bin/bash
set -e
pg_restore "$1" | sed 's/Owner: (studiocredo|postgres|cloudsqlsuperuser|cloudsqladmin)/Owner: vhs/g' | sed 's/(OWNER TO|TO|FROM) (postgres|studiocredo|cloudsqlsuperuser|cloudsqladmin)/$1 vhs/g' | psql credo
echo 'update "user_detail" set email = '"'webmaster@studiocredo.be'" | psql credo
