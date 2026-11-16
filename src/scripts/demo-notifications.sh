#!/usr/bin/env bash
# Async e-mails (notification-service) for Git Bash / Linux / macOS.
#
#   1. PUT /api/v1/admin/users/{id} through HAProxy returns at once.
#   2. After commit user-service hands the e-mail to notification-service on a user-async-* thread,
#      notification-service stores it in the outbox (PENDING) and sends it on a notif-async-* thread
#      (emulated provider latency MAIL_PROVIDER_LATENCY=1s).
#   3. The e-mail appears in Mailpit later than the HTTP response: the caller was not blocked.
#   4. The script restores the original last name (that sends one more "account changed" e-mail).
#
# Usage (from src/):  ./scripts/demo-notifications.sh [username]      default username: worker
# Needs: docker compose stack running, curl, python.

set -euo pipefail

TARGET_USER="${1:-worker}"
ADMIN_USER="${ADMIN_USER:-superadmin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-superadmin}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN_URL="${TOKEN_URL:-http://localhost:8084/realms/bricklayers/protocol/openid-connect/token}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
MAIL_TIMEOUT_SECONDS="${MAIL_TIMEOUT_SECONDS:-15}"

cd "$(dirname "$0")/.."

step() { printf '\n== %s\n' "$*"; }
now_ms() { date +%s%3N; }
# Reads JSON from stdin and prints the Python expression evaluated against it as `d`.
json() { python -c "import sys, json; d = json.load(sys.stdin); print($1)"; }

mail_count() {
    curl -s -G "$MAILPIT_URL/api/v1/search" --data-urlencode "query=to:$1" | json "d['messages_count']"
}

show_outbox() {
    docker compose exec -T notifications-db psql -U notifications_service -d notifications_db -c \
        "SELECT type, status, attempts, created_at, sent_at FROM notification_outbox
         WHERE recipient = '$1' ORDER BY created_at DESC LIMIT 3"
}

put_last_name() { # $1 = new last name; prints "<http status> <ms> <instance>"
    local body started status headers
    body=$(USER_JSON="$USER_JSON" LAST_NAME="$1" python -c "
import json, os
u = json.loads(os.environ['USER_JSON'])
print(json.dumps({'firstName': u['firstName'], 'lastName': os.environ['LAST_NAME'], 'email': u['email'],
                  'role': u['role'], 'companyId': u['companyId'], 'active': u['active']}))")
    headers=$(mktemp)
    started=$(now_ms)
    status=$(curl -s -o /dev/null -D "$headers" -w '%{http_code}' -X PUT "$BASE_URL/api/v1/admin/users/$USER_ID" \
        -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json; charset=utf-8' --data "$body")
    echo "$status $(( $(now_ms) - started )) $(grep -i '^x-instance-id' "$headers" | cut -d' ' -f2 | tr -d '\r')"
    rm -f "$headers"
}

step "Token for $ADMIN_USER"
TOKEN=$(curl -s -d grant_type=password -d client_id=bricklayers-load-test \
    -d "username=$ADMIN_USER" -d "password=$ADMIN_PASSWORD" "$TOKEN_URL" | json "d['access_token']")
echo "  ok"

step "Find user '$TARGET_USER'"
USER_JSON=$(curl -s "$BASE_URL/api/v1/admin/users?page=0&size=100" -H "Authorization: Bearer $TOKEN" |
    TARGET="$TARGET_USER" python -c "
import sys, json, os
users = [u for u in json.load(sys.stdin)['content'] if u['username'] == os.environ['TARGET']]
print(json.dumps(users[0]) if users else '')")
if [ -z "$USER_JSON" ]; then echo "  user '$TARGET_USER' not found"; exit 1; fi
USER_ID=$(echo "$USER_JSON" | json "d['id']")
EMAIL=$(echo "$USER_JSON" | json "d['email']")
ORIGINAL_LAST_NAME=$(echo "$USER_JSON" | json "d['lastName']")
echo "  id=$USER_ID email=$EMAIL lastName=$ORIGINAL_LAST_NAME"

step "PUT: change the last name (the e-mail is sent asynchronously)"
BEFORE=$(mail_count "$EMAIL")
MARKER="Async$(date +%H%M%S)"
read -r STATUS MS INSTANCE < <(put_last_name "$MARKER")
echo "  PUT -> $STATUS in $MS ms (X-Instance-ID=$INSTANCE), lastName=$MARKER"
[ "$STATUS" = 200 ] || { echo "  PUT failed"; exit 1; }

step "Wait for the e-mail in Mailpit ($MAILPIT_URL)"
WAIT_STARTED=$(now_ms)
until [ "$(mail_count "$EMAIL")" -gt "$BEFORE" ]; do
    if [ $(( $(now_ms) - WAIT_STARTED )) -gt $(( MAIL_TIMEOUT_SECONDS * 1000 )) ]; then
        echo "  no e-mail within $MAIL_TIMEOUT_SECONDS s (is mailpit running? see the outbox below)"
        show_outbox "$EMAIL"
        exit 1
    fi
    sleep 0.2
done
echo "  e-mail arrived $(( $(now_ms) - WAIT_STARTED )) ms AFTER the HTTP response (response took $MS ms)"
echo "  -> the caller was not blocked by the ~1 s e-mail provider"

step "notification_outbox (notifications_db)"
show_outbox "$EMAIL"

step "Threads that did the work"
echo "  user-service (hand-off to notification-service):"
docker compose logs --since 2m user-service 2>/dev/null | grep -E 'user-async-' | tail -n 2 | sed 's/^/    /' || true
echo "  notification-service (SMTP send):"
docker compose logs --since 2m notification-service 2>/dev/null | grep -E 'notif-async-' | tail -n 2 | sed 's/^/    /' || true

step "Restore the last name"
read -r STATUS MS INSTANCE < <(put_last_name "$ORIGINAL_LAST_NAME")
echo "  PUT -> $STATUS in $MS ms, lastName=$ORIGINAL_LAST_NAME"
echo
echo "Mailpit UI: $MAILPIT_URL"
