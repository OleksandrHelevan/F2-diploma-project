#!/bin/sh
set -eu

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "replica: empty data directory, cloning the primary $PRIMARY_HOST with pg_basebackup"
  until pg_isready -h "$PRIMARY_HOST" -p 5432 -U "$REPLICATION_USER" >/dev/null 2>&1; do
    sleep 1
  done
  SLOT_ARGS=""
  if [ -n "${REPLICATION_SLOT:-}" ]; then
    SLOT_ARGS="-S $REPLICATION_SLOT"
  fi
  PGPASSWORD="$REPLICATION_PASSWORD" pg_basebackup \
    -h "$PRIMARY_HOST" -p 5432 -U "$REPLICATION_USER" \
    -D "$PGDATA" -X stream -R $SLOT_ARGS -P
  chmod 0700 "$PGDATA"
  echo "replica: base backup finished, starting as a hot standby"
fi

exec postgres \
  -c hot_standby=on \
  -c hot_standby_feedback=on \
  -c recovery_min_apply_delay="${REPLICA_APPLY_DELAY:-0}"
