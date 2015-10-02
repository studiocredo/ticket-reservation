#!/bin/bash
set -e
pg_restore "$1" | sed 's/Owner: studiocredo/Owner: vhs/g' | sed 's/Owner: postgres/Owner: vhs/g' | sed 's/TO postgres/TO vhs/g' | sed 's/FROM postgres/FROM vhs/' | sed 's/OWNER TO studiocredo/OWNER TO vhs/g' | psql credo
echo 'update "user_detail" set email = '"'webmaster@studiocredo.be'" | psql credo
